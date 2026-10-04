import React, { useCallback, useState } from "react";
import { View, ScrollView, Text, StyleSheet, ActivityIndicator, Alert } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { useFocusEffect } from "@react-navigation/native";
import { colors } from "@/constants/theme";
import { getMyPosts } from "@/api/post";
import { cancelApplication, getMyApplications } from "@/api/application";
import { clearFcmToken } from "@/api/auth";
import { deleteMyAccount } from "@/api/user";
import { ApiError } from "@/lib/apiClient";
import { useAuth } from "@/contexts/AuthContext";
import { PostSummary } from "@/types/post";
import { ApplicationSummary } from "@/types/application";
import AppHeader from "@/components/common/AppHeader";
import PostCard from "@/components/post/PostCard";
import ProfileSection from "@/components/myPage/ProfileSection";
import TabbedPager from "@/components/myPage/TabbedPager";
import ApplicationCard from "@/components/myPage/ApplicationCard";
import SettingsSection from "@/components/myPage/SettingsSection";
import ThemedButton from "@/components/common/ThemedButton";

const TABS = [
  { key: "posts", label: "내 모집글" },
  { key: "applications", label: "내 지원" },
];

// 프로필(AuthContext)·내 모집글·내 지원을 모두 실 API로 불러온다. 이 화면은 바깥이 이미 ScrollView라
// 무한스크롤 구조가 아니어서 두 목록 모두 한 번에 서버 상한(50건)까지 받는다. 통계 숫자는 이 목록 기준이고,
// 50건을 넘으면 "50+"처럼 더 있음을 표시한다.
const MY_LIST_SIZE = 50;

// 통계 숫자 표시. 서버에 더 있으면(받은 범위를 넘으면) "50+"처럼 뒤에 +를 붙인다
const withMoreMark = (count: number, hasMore: boolean) => (hasMore ? `${count}+` : count);

export default function MyPageScreen() {
  const router = useRouter();
  const { profile, profileError, refreshProfile, signOut } = useAuth();

  const [myPosts, setMyPosts] = useState<PostSummary[]>([]);
  const [postsLoading, setPostsLoading] = useState(true);
  const [postsError, setPostsError] = useState<string | null>(null);
  // 서버에 더 있는데 한 번에 받은 범위를 넘은 경우(통계에 "+" 표시)
  const [postsHasMore, setPostsHasMore] = useState(false);

  const [applications, setApplications] = useState<ApplicationSummary[]>([]);
  const [applicationsLoading, setApplicationsLoading] = useState(true);
  const [applicationsError, setApplicationsError] = useState<string | null>(null);
  const [applicationsHasMore, setApplicationsHasMore] = useState(false);
  // 지원 취소 요청 중인 지원서 id(없으면 null)
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  // 로딩 스피너는 첫 조회에만 보이고(초기 state가 true), 이후 포커스 복귀 때의 재조회는 화면을 비우지 않고
  // 조용히 갱신한다 — 모집글 수정·삭제나 지원자 처리 후 돌아왔을 때 목록이 낡지 않게 하려는 재조회다.
  const loadMyPosts = useCallback(async () => {
    setPostsError(null);
    try {
      const page = await getMyPosts(0, MY_LIST_SIZE);
      setMyPosts(page.content);
      setPostsHasMore(page.hasNext);
    } catch (err) {
      setPostsError(err instanceof ApiError ? err.message : "모집글을 불러오지 못했어요.");
    } finally {
      setPostsLoading(false);
    }
  }, []);

  const loadApplications = useCallback(async () => {
    setApplicationsError(null);
    try {
      const page = await getMyApplications(0, MY_LIST_SIZE);
      setApplications(page.content);
      setApplicationsHasMore(page.hasNext);
    } catch (err) {
      setApplicationsError(err instanceof ApiError ? err.message : "지원 목록을 불러오지 못했어요.");
    } finally {
      setApplicationsLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      loadMyPosts();
      loadApplications();
    }, [loadMyPosts, loadApplications])
  );

  const acceptedCount = applications.filter((a) => a.status === "ACCEPTED").length;

  // 지원 취소(검토 중인 지원만). 취소해도 같은 글에 다시 지원할 수 있어 확인만 거친다.
  // PATCH지만 멱등이 아니라(이미 처리된 지원은 400) 요청 중인 지원서는 버튼을 잠근다.
  // 성공하면 목록을 다시 조회해 상태를 서버 값으로 맞춘다
  const handleCancelApplication = (application: ApplicationSummary) => {
    Alert.alert("지원 취소", `'${application.post.title}' 지원을 취소할까요? 취소한 뒤에도 다시 지원할 수 있어요.`, [
      { text: "닫기", style: "cancel" },
      {
        text: "지원 취소",
        style: "destructive",
        onPress: async () => {
          if (cancellingId !== null) return;
          setCancellingId(application.id);
          try {
            await cancelApplication(application.id);
            await loadApplications();
          } catch (err) {
            Alert.alert("취소 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
          } finally {
            setCancellingId(null);
          }
        },
      },
    ]);
  };

  // 이 기기의 푸시 토큰을 서버에서 해제한다. 토큰은 기기에 속해서, 해제하지 않으면 같은 기기에서 다른 계정으로
  // 로그인했을 때 이전 계정의 알림이 도착한다. 실패해도 로그아웃·탈퇴를 막지 않는다(best-effort)
  const releasePushToken = async () => {
    try {
      await clearFcmToken();
    } catch {
      // 네트워크 문제 등은 무시 — 서버는 다음 로그인 때 토큰 소유 계정을 바로잡는다
    }
  };

  // 로그아웃 — 푸시 토큰을 먼저 해제한 뒤 Firebase 세션을 끊으면 RootNavigator가 로그인 화면으로 자동 이동시킨다
  const handleLogout = async () => {
    await releasePushToken();
    try {
      await signOut();
    } catch {
      Alert.alert("로그아웃 실패", "잠시 후 다시 시도해주세요.");
    }
  };

  // 회원탈퇴 — 푸시 토큰 해제 → 서버에서 계정 삭제 → 로그아웃 순서. 서버 삭제에 실패하면(예: 연관 데이터 충돌)
  // 로그인 상태를 유지한다. 서버 삭제가 끝난 뒤의 로그아웃 실패는 탈퇴가 이미 끝난 것이므로 "탈퇴 실패"로 보이지 않게 따로 안내한다.
  const handleWithdraw = async () => {
    await releasePushToken();
    try {
      await deleteMyAccount();
    } catch (err) {
      Alert.alert("탈퇴 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
      return;
    }
    try {
      await signOut();
    } catch {
      Alert.alert("탈퇴가 완료되었어요", "계정과 데이터가 삭제되었어요. 앱을 다시 실행하면 로그인 화면으로 돌아갑니다.");
    }
  };

  // 프로필이 없으면 이 화면의 나머지(통계·탭)도 의미가 없어 조회 중/실패 상태만 보여준다.
  // 로그아웃·탈퇴 버튼은 실패 상태에서도 쓸 수 있어야 하므로 설정 섹션은 항상 렌더한다.
  const profileBlock = profile ? (
    <ProfileSection
      user={profile}
      myPostCount={withMoreMark(myPosts.length, postsHasMore)}
      totalApplications={withMoreMark(applications.length, applicationsHasMore)}
      acceptedApplications={withMoreMark(acceptedCount, applicationsHasMore)}
      onEditPress={() => router.push("/my-page/edit")}
    />
  ) : profileError ? (
    <View style={styles.profileError}>
      <Text style={styles.emptyText}>{profileError}</Text>
      <ThemedButton title="다시 시도" variant="outline" onPress={refreshProfile} style={styles.retryButton} />
    </View>
  ) : (
    <ActivityIndicator style={styles.tabSpinner} color={colors.primary} />
  );

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <AppHeader />
      <ScrollView showsVerticalScrollIndicator={false}>
        {profileBlock}

        <TabbedPager
          tabs={TABS}
          pages={[
            postsLoading ? (
              <ActivityIndicator style={styles.tabSpinner} color={colors.primary} />
            ) : postsError ? (
              <Text style={styles.emptyText}>{postsError}</Text>
            ) : myPosts.length === 0 ? (
              <Text style={styles.emptyText}>등록한 모집글이 없어요.</Text>
            ) : (
              myPosts.map((post, i) => (
                <View key={post.id} style={i > 0 ? { marginTop: 12 } : undefined}>
                  <PostCard
                    post={post}
                    showStatus
                    onPress={(id) => router.push({ pathname: "/post/[id]", params: { id } })}
                  />
                </View>
              ))
            ),
            applicationsLoading ? (
              <ActivityIndicator style={styles.tabSpinner} color={colors.primary} />
            ) : applicationsError ? (
              <Text style={styles.emptyText}>{applicationsError}</Text>
            ) : applications.length === 0 ? (
              <Text style={styles.emptyText}>지원한 모집글이 없어요.</Text>
            ) : (
              applications.map((app, i) => (
                <View key={app.id} style={i > 0 ? { marginTop: 12 } : undefined}>
                  <ApplicationCard
                    item={app}
                    cancelling={cancellingId === app.id}
                    onCancel={() => handleCancelApplication(app)}
                  />
                </View>
              ))
            ),
          ]}
        />

        <SettingsSection
          onBlocksPress={() => router.push("/my-page/blocks")}
          onLogout={handleLogout}
          onWithdraw={handleWithdraw}
        />

        <Text style={styles.versionText}>v0.1.0</Text>
        <View style={{ height: 40 }} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  emptyText: {
    textAlign: "center",
    color: colors.textMuted,
    fontSize: 14,
    marginTop: 40,
  },
  tabSpinner: {
    marginTop: 40,
  },
  profileError: {
    alignItems: "center",
    paddingBottom: 16,
  },
  retryButton: {
    marginTop: 16,
    paddingHorizontal: 32,
  },
  versionText: {
    textAlign: "center",
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 16,
  },
});
