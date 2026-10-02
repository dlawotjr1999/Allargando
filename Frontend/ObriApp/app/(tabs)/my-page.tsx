import React, { useCallback, useState } from "react";
import { View, ScrollView, Text, StyleSheet, ActivityIndicator, Alert } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { useFocusEffect } from "@react-navigation/native";
import { colors } from "@/constants/theme";
import { getMyPosts } from "@/api/post";
import { getMyApplications } from "@/api/application";
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
// 무한스크롤 구조가 아니어서 두 목록 모두 첫 페이지(10건)만 조회한다 — 건수가 페이지 크기를 넘는
// 경우는 아직 드물다고 보고 후순위로 미룸. 통계 숫자도 이 첫 페이지 기준이다.
export default function MyPageScreen() {
  const router = useRouter();
  const { profile, profileError, refreshProfile, signOut } = useAuth();
  const [notifEnabled, setNotifEnabled] = useState(true);

  const [myPosts, setMyPosts] = useState<PostSummary[]>([]);
  const [postsLoading, setPostsLoading] = useState(true);
  const [postsError, setPostsError] = useState<string | null>(null);

  const [applications, setApplications] = useState<ApplicationSummary[]>([]);
  const [applicationsLoading, setApplicationsLoading] = useState(true);
  const [applicationsError, setApplicationsError] = useState<string | null>(null);

  // 로딩 스피너는 첫 조회에만 보이고(초기 state가 true), 이후 포커스 복귀 때의 재조회는 화면을 비우지 않고
  // 조용히 갱신한다 — 모집글 수정·삭제나 지원자 처리 후 돌아왔을 때 목록이 낡지 않게 하려는 재조회다.
  const loadMyPosts = useCallback(async () => {
    setPostsError(null);
    try {
      const page = await getMyPosts(0);
      setMyPosts(page.content);
    } catch (err) {
      setPostsError(err instanceof ApiError ? err.message : "모집글을 불러오지 못했어요.");
    } finally {
      setPostsLoading(false);
    }
  }, []);

  const loadApplications = useCallback(async () => {
    setApplicationsError(null);
    try {
      const page = await getMyApplications(0);
      setApplications(page.content);
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

  // 로그아웃 — Firebase 세션을 끊으면 RootNavigator가 로그인 화면으로 자동 이동시킨다
  const handleLogout = async () => {
    try {
      await signOut();
    } catch {
      Alert.alert("로그아웃 실패", "잠시 후 다시 시도해주세요.");
    }
  };

  // 회원탈퇴 — 서버에서 계정을 지운 뒤 로그아웃한다. 서버 삭제에 실패하면(예: 연관 데이터 충돌) 로그인 상태를 유지한다.
  const handleWithdraw = async () => {
    try {
      await deleteMyAccount();
      await signOut();
    } catch (err) {
      Alert.alert("탈퇴 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
    }
  };

  // 프로필이 없으면 이 화면의 나머지(통계·탭)도 의미가 없어 조회 중/실패 상태만 보여준다.
  // 로그아웃·탈퇴 버튼은 실패 상태에서도 쓸 수 있어야 하므로 설정 섹션은 항상 렌더한다.
  const profileBlock = profile ? (
    <ProfileSection
      user={profile}
      myPostCount={myPosts.length}
      totalApplications={applications.length}
      acceptedApplications={acceptedCount}
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
                  <ApplicationCard item={app} />
                </View>
              ))
            ),
          ]}
        />

        <SettingsSection
          notifEnabled={notifEnabled}
          onToggleNotif={setNotifEnabled}
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
