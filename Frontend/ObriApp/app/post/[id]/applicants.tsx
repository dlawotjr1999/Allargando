import React, { useCallback, useEffect, useState } from "react";
import { View, Text, FlatList, StyleSheet, ActivityIndicator, Alert } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useLocalSearchParams } from "expo-router";
import { colors } from "@/constants/theme";
import { getApplicationsByPostId, acceptApplication, rejectApplication, revokeApplication } from "@/api/application";
import { ApiError } from "@/lib/apiClient";
import { ApplicationSummary } from "@/types/application";
import { ReportTarget } from "@/types/safety";
import { confirmBlockUser } from "@/lib/safety";
import { markPostListStale } from "@/lib/postListRefresh";
import ScreenHeader from "@/components/common/ScreenHeader";
import EmptyState from "@/components/common/EmptyState";
import ApplicantCard from "@/components/application/ApplicantCard";
import ReportDialog from "@/components/report/ReportDialog";

// 지원자 목록(모집자 전용). GET /api/applications/post/{postId}는 모집자 본인만 200이고
// 그 외엔 403이 나므로(ApplicationAccessPolicy.requireRecruiter), 별도 프론트 접근 제어 없이
// 에러 응답을 그대로 화면에 보여주는 것으로 충분하다 — 어차피 이 화면 진입 버튼 자체가
// post/[id]/index.tsx에서 isMine일 때만 노출된다.
export default function ApplicantsScreen() {
  const { id, title } = useLocalSearchParams<{ id: string; title?: string }>();

  const [applications, setApplications] = useState<ApplicationSummary[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 수락/거절/철회 중인 지원서 id — 해당 카드의 버튼만 잠근다(전체 목록을 잠그지 않음).
  // accept/reject/revoke는 PATCH지만 상태 전이가 끝난 지원서에 재호출하면 400이 나는 비멱등 동작이라
  // 이 잠금이 실질적인 중복 요청 방지 장치다.
  const [processingId, setProcessingId] = useState<number | null>(null);
  // 신고 중인 대상(null이면 신고 창 닫힘)
  const [reportTarget, setReportTarget] = useState<ReportTarget | null>(null);

  // silent가 true면 스피너·오류 화면 없이 목록만 조용히 갱신한다(처리 직후 서버 값과 맞추는 용도 —
  // 실패해도 이미 반영한 로컬 상태를 그대로 둔다)
  const loadFirstPage = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const page = await getApplicationsByPostId(Number(id), 0);
      setApplications(page.content);
      setCurrentPage(page.currentPage);
      setHasNext(page.hasNext);
    } catch (err) {
      if (!silent) setError(err instanceof ApiError ? err.message : "지원자 목록을 불러오지 못했어요.");
    } finally {
      if (!silent) setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadFirstPage();
  }, [loadFirstPage]);

  const loadNextPage = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    try {
      const page = await getApplicationsByPostId(Number(id), currentPage + 1);
      setApplications((prev) => [...prev, ...page.content]);
      setCurrentPage(page.currentPage);
      setHasNext(page.hasNext);
    } catch {
      // 다음 페이지 실패는 조용히 무시 — 이미 보여준 목록은 그대로 유지, 스크롤하면 재시도됨
    } finally {
      setLoadingMore(false);
    }
  };

  // 수락/거절/철회 공통 처리. 성공하면 먼저 로컬 목록의 해당 항목만 새 상태로 바꿔 즉시 보여준다.
  // 수락·철회는 악기 정원이 바뀌어 서버가 같은 악기의 다른 대기 지원을 자동 거절하기도 하므로(D10)
  // 목록을 조용히 다시 조회해 맞추고, 모집글 목록(홈)의 모집 현황도 낡았다고 표시한다.
  // 거절은 정원에 영향이 없어 로컬 갱신만으로 충분하다.
  const applyLocalStatus = (applicationId: number, status: ApplicationSummary["status"]) => {
    setApplications((prev) =>
      prev.map((a) => (a.id === applicationId ? { ...a, status } : a))
    );
  };

  const runAction = async (
    applicationId: number,
    action: (id: number) => Promise<void>,
    nextStatus: ApplicationSummary["status"],
    failTitle: string
  ) => {
    if (processingId) return;
    setProcessingId(applicationId);
    try {
      await action(applicationId);
      applyLocalStatus(applicationId, nextStatus);
      if (nextStatus === "ACCEPTED" || nextStatus === "REVOKED") {
        markPostListStale();
        await loadFirstPage(true);
      }
    } catch (err) {
      Alert.alert(failTitle, err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
    } finally {
      setProcessingId(null);
    }
  };

  // 지원자 신고·차단 메뉴. 지원서 추가 정보처럼 글이 아닌 부적절한 내용은 그 지원자를 신고하면 된다.
  // 이미 들어온 지원은 차단해도 남는다(목록에서 거절하면 됨) — 차단은 이후의 새 지원과 모집글 노출을 막는다
  const openApplicantMenu = (application: ApplicationSummary) => {
    const nickname = application.applicant.nickname;
    Alert.alert(nickname, undefined, [
      { text: "신고", onPress: () => setReportTarget({ type: "USER", nickname }) },
      { text: "차단", style: "destructive", onPress: () => confirmBlockUser(nickname) },
      { text: "취소", style: "cancel" },
    ]);
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.headerArea}>
        <ScreenHeader />
        <Text style={styles.headerTitle}>지원자 목록</Text>
        {title ? (
          <Text style={styles.headerSubtitle} numberOfLines={1}>
            {title}
          </Text>
        ) : null}
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : error ? (
        <EmptyState icon="cloud-offline-outline" title="목록을 불러오지 못했어요" description={error} />
      ) : (
        <FlatList
          data={applications}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <ApplicantCard
              application={item}
              processing={processingId === item.id}
              onMore={() => openApplicantMenu(item)}
              onAccept={() =>
                runAction(item.id, acceptApplication, "ACCEPTED", "수락 실패")
              }
              onReject={() =>
                runAction(item.id, rejectApplication, "REJECTED", "거절 실패")
              }
              onRevoke={() =>
                runAction(item.id, revokeApplication, "REVOKED", "철회 실패")
              }
            />
          )}
          contentContainerStyle={styles.listContent}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          onEndReached={loadNextPage}
          onEndReachedThreshold={0.4}
          ListFooterComponent={
            loadingMore ? (
              <ActivityIndicator style={styles.footerSpinner} color={colors.primary} />
            ) : null
          }
          ListEmptyComponent={
            <EmptyState
              icon="people-outline"
              title="아직 지원자가 없어요"
              description="새로운 지원이 도착하면 여기에 표시돼요."
            />
          }
        />
      )}

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
    gap: 2,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: "700",
    color: colors.textPrimary,
    marginTop: 8,
  },
  headerSubtitle: {
    fontSize: 13,
    color: colors.textMuted,
    marginBottom: 8,
  },
  centerFill: { flex: 1, alignItems: "center", justifyContent: "center" },
  listContent: { flexGrow: 1, padding: 16 },
  separator: { height: 12 },
  footerSpinner: { marginVertical: 16 },
});
