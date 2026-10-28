import React, { useCallback, useState } from "react";
import { View, ScrollView, Text, StyleSheet, ActivityIndicator, Alert, TouchableOpacity } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useFocusEffect, useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { getMyPosts } from "@/api/post";
import { cancelApplication, getMyApplications } from "@/api/application";
import { ApiError } from "@/lib/apiClient";
import { useAuth } from "@/contexts/AuthContext";
import { PostSummary } from "@/types/post";
import { ApplicationSummary } from "@/types/application";
import AppHeader from "@/components/common/AppHeader";
import PostCard from "@/components/post/PostCard";
import ProfileSection from "@/components/myPage/ProfileSection";
import TabbedPager from "@/components/myPage/TabbedPager";
import ApplicationCard from "@/components/myPage/ApplicationCard";
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
  const { profile, profileError, refreshProfile } = useAuth();

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

  // 프로필이 없으면 이 화면의 나머지(통계·탭)도 의미가 없어 조회 중/실패 상태만 보여준다.
  // 로그아웃·탈퇴는 실패 상태에서도 쓸 수 있어야 하므로 설정 버튼은 프로필과 무관한 상단 줄에 둔다.
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
      <View style={styles.pageHeader}>
        <Text style={styles.pageTitle}>마이페이지</Text>
        <TouchableOpacity
          onPress={() => router.push("/my-page/settings")}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
          accessibilityRole="button"
          accessibilityLabel="설정"
        >
          <Ionicons name="settings-outline" size={22} color={colors.primary} />
        </TouchableOpacity>
      </View>
      <ScrollView showsVerticalScrollIndicator={false}>
        {profileBlock}

        <TabbedPager
          tabs={TABS}
          pages={[
            postsLoading ? (
              <ActivityIndicator style={styles.tabSpinner} color={colors.primary} />
            ) : postsError ? (
              <View style={styles.profileError}>
                <Text style={styles.emptyText}>{postsError}</Text>
                <ThemedButton
                  title="다시 시도"
                  variant="outline"
                  onPress={() => loadMyPosts()}
                  style={styles.retryButton}
                />
              </View>
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
              <View style={styles.profileError}>
                <Text style={styles.emptyText}>{applicationsError}</Text>
                <ThemedButton
                  title="다시 시도"
                  variant="outline"
                  onPress={() => loadApplications()}
                  style={styles.retryButton}
                />
              </View>
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
  // 연습일지 탭의 제목 줄과 같은 모양 — 오른쪽 끝에 설정 버튼
  pageHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  pageTitle: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.primary,
  },
});
