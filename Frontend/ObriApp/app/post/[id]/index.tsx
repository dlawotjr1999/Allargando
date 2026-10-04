import React, { useCallback, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  ActivityIndicator,
  Alert,
  TouchableOpacity,
} from "react-native";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useFocusEffect } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { closePost, deletePost, getPost } from "@/api/post";
import { submitApplication } from "@/api/application";
import { ApiError } from "@/lib/apiClient";
import { useAuth } from "@/contexts/AuthContext";
import { ApplicationStatus } from "@/types/application";
import { PostDetail } from "@/types/post";
import { ReportTarget } from "@/types/safety";
import { markPostListStale } from "@/lib/postListRefresh";
import { confirmBlockUser } from "@/lib/safety";
import { formatEventDateTime } from "@/utils/datetime";
import ScreenHeader from "@/components/common/ScreenHeader";
import EmptyState from "@/components/common/EmptyState";
import IconText from "@/components/common/IconText";
import Tag from "@/components/common/Tag";
import ThemedButton from "@/components/common/ThemedButton";
import ApplicationSubmitModal from "@/components/application/ApplicationSubmitModal";
import ActionMenu, { ActionMenuItem } from "@/components/common/ActionMenu";
import ReportDialog from "@/components/report/ReportDialog";

// 이미 지원한 글의 버튼 문구(취소는 다시 지원할 수 있어 여기 없음)
const MY_STATUS_LABEL: Record<Exclude<ApplicationStatus, "CANCELLED">, string> = {
  PENDING: "지원 완료 · 검토 중",
  ACCEPTED: "수락된 지원",
  REJECTED: "거절된 지원",
  REVOKED: "수락이 철회된 지원",
};

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {children}
    </View>
  );
}

export default function PostDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { profile } = useAuth();

  const [post, setPost] = useState<PostDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [applyModalVisible, setApplyModalVisible] = useState(false);
  const [submittingApplication, setSubmittingApplication] = useState(false);
  const [menuVisible, setMenuVisible] = useState(false);
  // 신고 중인 대상(null이면 신고 창 닫힘)
  const [reportTarget, setReportTarget] = useState<ReportTarget | null>(null);

  // 단건 조회. id가 잘못됐거나(404) 이미 삭제된 글이면 백엔드가 NotFoundException(404)을 던지므로
  // 그 경우도 "찾을 수 없음" EmptyState로 자연스럽게 합류시킨다(별도 404 분기 불필요).
  // 로딩 스피너는 첫 조회에만 보이고(초기 state가 true), 이후 재조회(지원·마감 직후, 수정·지원자 화면에서
  // 돌아왔을 때)는 화면을 비우지 않고 조용히 값만 갱신한다.
  const loadPost = useCallback(async () => {
    setError(null);
    try {
      const result = await getPost(Number(id));
      setPost(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "모집글을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  // 포커스될 때마다 재조회 — 수정 화면·지원자 관리 화면에서 돌아오면 바뀐 내용·지원자 수가 바로 반영된다
  useFocusEffect(
    useCallback(() => {
      loadPost();
    }, [loadPost])
  );

  if (loading) {
    return (
      <SafeAreaView style={styles.container} edges={["top"]}>
        <View style={styles.headerArea}>
          <ScreenHeader />
        </View>
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      </SafeAreaView>
    );
  }

  if (!post) {
    return (
      <SafeAreaView style={styles.container} edges={["top"]}>
        <View style={styles.headerArea}>
          <ScreenHeader />
        </View>
        <EmptyState
          icon="alert-circle-outline"
          title="모집글을 찾을 수 없어요"
          description={error ?? "삭제되었거나 존재하지 않는 모집글입니다."}
        />
      </SafeAreaView>
    );
  }

  // closed는 서버가 confirmed>=people로 이미 계산해 내려주는 값 — 프론트에서 다시 비교하지 않는다.
  // 지원 악기는 모달에서 모집 악기 중 직접 고르므로, 모든 악기가 찼을 때만 지원이 막힌다.
  const allInstrumentsClosed = post.instruments.every((it) => it.closed);

  // isMine·myApplicationStatus는 서버가 로그인 유저 기준으로 계산해 내려주는 값을 그대로 쓴다
  // (PostDetailResponseDTO) — 별도 목록을 프론트에서 대조해 재계산하지 않는다.
  const isMyPost = post.isMine;
  const myStatus = post.myApplicationStatus;
  const eventPassed = new Date(post.eventAt) < new Date();
  const isClosed = post.status === "CLOSED";

  // 지원 버튼 문구·활성 여부. 이미 지원한 글은 마감·공연 종료와 상관없이 내 지원 상태를 먼저 보여준다.
  // 취소(CANCELLED)한 지원만 같은 글에 다시 지원할 수 있고(D7) 대기·수락·거절·철회는 서버가 409로 막는다.
  // 그 외(지원한 적 없음·취소)는 ApplicationService.submitApplication의 검증 순서(마감글 → 공연종료 →
  // 정원)와 맞춘다. 본인 글(isMyPost)은 이 버튼 대신 "지원자 보기"가 나오므로 다루지 않는다.
  let applyLabel = myStatus === "CANCELLED" ? "다시 지원하기" : "지원하기";
  let applyDisabled = false;
  if (myStatus && myStatus !== "CANCELLED") {
    applyLabel = MY_STATUS_LABEL[myStatus];
    applyDisabled = true;
  } else if (isClosed) {
    applyLabel = "마감된 모집글";
    applyDisabled = true;
  } else if (eventPassed) {
    applyLabel = "종료된 공연";
    applyDisabled = true;
  } else if (allInstrumentsClosed) {
    applyLabel = "모든 악기 정원이 마감됐어요";
    applyDisabled = true;
  }

  // 지원 제출. 성공하면 모달을 닫고 단건 조회를 다시 실행해 myApplicationStatus·applicationCount를
  // 서버 최신 값으로 갱신한다(로컬에서 낙관적으로 바꾸지 않는 이유: applicationCount처럼
  // 이 화면이 직접 계산할 수 없는 값도 같이 바뀌므로, 재조회가 더 단순하고 정확하다).
  const handleSubmitApplication = async (instrument: string, additionalInfo: string) => {
    if (submittingApplication) return; // 제출은 POST라 비멱등 — 연속 탭 시 지원이 두 번 생기지 않도록 잠금
    setSubmittingApplication(true);
    try {
      await submitApplication({
        postId: post.id,
        instrument,
        additionalInfo: additionalInfo.trim() || undefined,
      });
      setApplyModalVisible(false);
      await loadPost();
    } catch (err) {
      Alert.alert(
        "지원 실패",
        err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요."
      );
    } finally {
      setSubmittingApplication(false);
    }
  };

  // 모집 마감 (작성자만). 마감하면 더 이상 지원을 받을 수 없어 되돌리는 UI가 없으므로 확인을 거친다.
  const handleClosePost = () => {
    Alert.alert("모집 마감", "마감하면 더 이상 지원을 받을 수 없어요. 마감할까요?", [
      { text: "취소", style: "cancel" },
      {
        text: "마감",
        style: "destructive",
        onPress: async () => {
          try {
            await closePost(post.id);
            await loadPost();
          } catch (err) {
            Alert.alert("마감 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
          }
        },
      },
    ]);
  };

  // 모집글 삭제 (작성자만). 지원 내역도 서버에서 함께 정리되고 되돌릴 수 없어 확인을 거친다.
  // 성공하면 이 글은 더 없으므로 이전 화면으로 돌아간다.
  const handleDeletePost = () => {
    Alert.alert("모집글 삭제", "삭제하면 지원 내역도 함께 사라지고 되돌릴 수 없어요. 삭제할까요?", [
      { text: "취소", style: "cancel" },
      {
        text: "삭제",
        style: "destructive",
        onPress: async () => {
          try {
            await deletePost(post.id);
            markPostListStale();
            router.back();
          } catch (err) {
            Alert.alert("삭제 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
          }
        },
      },
    ]);
  };

  // 더보기 메뉴 — 내 글은 수정·마감·삭제, 남의 글은 글 신고·작성자 신고·작성자 차단(앱 내 신고·차단 경로)
  const menuItems: ActionMenuItem[] = isMyPost
    ? [
        {
          label: "수정",
          onPress: () => router.push({ pathname: "/post/[id]/edit", params: { id: String(post.id) } }),
        },
        ...(isClosed ? [] : [{ label: "모집 마감", onPress: handleClosePost }]),
        { label: "삭제", onPress: handleDeletePost, destructive: true },
      ]
    : [
        { label: "모집글 신고", onPress: () => setReportTarget({ type: "POST", postId: post.id }) },
        {
          label: "작성자 신고",
          onPress: () => setReportTarget({ type: "USER", nickname: post.writer.nickname }),
        },
        {
          // 차단하면 이 글도 목록에서 사라지므로 성공 후 목록으로 돌아간다
          label: "작성자 차단",
          onPress: () => confirmBlockUser(post.writer.nickname, () => router.back()),
          destructive: true,
        },
      ];

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.headerArea}>
        <ScreenHeader
          right={
            <TouchableOpacity
              onPress={() => setMenuVisible(true)}
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              accessibilityLabel={isMyPost ? "모집글 관리 메뉴" : "신고·차단 메뉴"}
            >
              <Ionicons name="ellipsis-horizontal" size={22} color={colors.primary} />
            </TouchableOpacity>
          }
        />
      </View>

      <ScrollView
        contentContainerStyle={[styles.scrollContent, { paddingBottom: 120 }]}
        showsVerticalScrollIndicator={false}
      >
        {/* 히어로: 썸네일 + 카테고리 + 제목 */}
        <View style={styles.hero}>
          <View style={styles.thumbnail}>
            <Ionicons name="musical-note" size={36} color={colors.primaryLight} />
          </View>
          <View style={styles.categoryRow}>
            <Tag label={post.category} variant="accent" />
            {isClosed && <Tag label="마감" />}
          </View>
          <Text style={styles.title}>{post.title}</Text>

          {/* 작성자 — 매너 점수는 백엔드에 아직 없는 향후 기능(REVIEWS 테이블 도입 전)이라 표시하지 않음.
              남의 글이면 눌러서 작성자의 공개 프로필(활동 이력·신고·차단)로 들어간다 */}
          <TouchableOpacity
            style={styles.writerRow}
            disabled={isMyPost}
            activeOpacity={0.7}
            onPress={() =>
              router.push({ pathname: "/user/[nickname]", params: { nickname: post.writer.nickname } })
            }
            accessibilityLabel={isMyPost ? undefined : `${post.writer.nickname} 프로필 보기`}
          >
            <Ionicons name="person-circle-outline" size={18} color={colors.textMuted} />
            <Text style={styles.writerText}>
              {post.writer.nickname} · {post.writer.instrument}
            </Text>
            {!isMyPost && <Ionicons name="chevron-forward" size={14} color={colors.textMuted} />}
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        {/* 기본 정보 */}
        <Section title="기본 정보">
          <View style={styles.infoList}>
            <IconText icon="calendar-outline" text={formatEventDateTime(post.eventAt)} />
            <IconText icon="location-outline" text={post.location} />
          </View>
        </Section>

        {/* 모집 악기 — 내 프로필 악기와 일치하는 항목을 강조 표시 */}
        <Section title="모집 악기">
          <View style={styles.tagRow}>
            {post.instruments.map((it) => (
              <Tag
                key={it.instrument}
                label={`${it.instrument} ${it.confirmed}/${it.people}`}
                variant={it.instrument === profile?.instrument ? "filled" : "outline"}
              />
            ))}
          </View>
        </Section>

        {/* 시간표 */}
        <Section title="시간표">
          <Text style={styles.bodyText}>{post.timetable}</Text>
        </Section>

        {/* 설명 */}
        {post.description ? (
          <Section title="설명">
            <Text style={styles.bodyText}>{post.description}</Text>
          </Section>
        ) : null}
      </ScrollView>

      {/* 하단 고정: 본인 글이면 지원자 관리로, 아니면 지원하기로 (우측) */}
      <View style={[styles.footer, { paddingBottom: insets.bottom + 12 }]}>
        {isMyPost ? (
          <ThemedButton
            title={`지원자 보기 (${post.applicationCount})`}
            style={styles.applyButton}
            onPress={() =>
              router.push({ pathname: "/post/[id]/applicants", params: { id: String(post.id) } })
            }
          />
        ) : (
          <ThemedButton
            title={applyLabel}
            disabled={applyDisabled}
            style={styles.applyButton}
            onPress={() => setApplyModalVisible(true)}
          />
        )}
      </View>

      <ApplicationSubmitModal
        visible={applyModalVisible}
        postTitle={post.title}
        instruments={post.instruments}
        defaultInstrument={profile?.instrument}
        submitting={submittingApplication}
        onSubmit={handleSubmitApplication}
        onClose={() => setApplyModalVisible(false)}
      />

      <ActionMenu visible={menuVisible} items={menuItems} onDismiss={() => setMenuVisible(false)} />

      <ReportDialog target={reportTarget} onClose={() => setReportTarget(null)} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  headerArea: {
    paddingHorizontal: 24,
    paddingTop: 8,
  },
  scrollContent: {
    paddingHorizontal: 24,
  },
  centerFill: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  hero: {
    alignItems: "flex-start",
  },
  thumbnail: {
    width: 72,
    height: 72,
    borderRadius: 16,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 16,
  },
  categoryRow: {
    flexDirection: "row",
    gap: 6,
    marginBottom: 10,
  },
  title: {
    fontSize: 22,
    fontWeight: "700",
    color: colors.textPrimary,
    lineHeight: 30,
  },
  writerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: 12,
  },
  writerText: {
    fontSize: 13,
    color: colors.textSecondary,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: 24,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 12,
    color: colors.textMuted,
    letterSpacing: 1,
    marginBottom: 12,
  },
  infoList: {
    gap: 10,
  },
  tagRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  bodyText: {
    fontSize: 14,
    color: colors.textSecondary,
    lineHeight: 22,
  },
  footer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: 24,
    paddingTop: 12,
    backgroundColor: colors.background,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    alignItems: "flex-end",
  },
  applyButton: {
    height: 48,
    paddingHorizontal: 40,
    borderRadius: 24,
  },
});
