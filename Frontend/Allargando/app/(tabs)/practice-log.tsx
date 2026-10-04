import React, { useCallback, useState } from "react";
import { View, FlatList, StyleSheet, TouchableOpacity, Text, ActivityIndicator, Alert } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { useFocusEffect } from "@react-navigation/native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { deletePracticeLog, getPracticeLog, getPracticeLogs } from "@/api/practiceLog";
import { ApiError } from "@/lib/apiClient";
import { PracticeLogDetail, PracticeLogSummary } from "@/types/practiceLog";
import AppHeader from "@/components/common/AppHeader";
import EmptyState from "@/components/common/EmptyState";
import PracticeLogCard from "@/components/practiceLog/PracticeLogCard";
import PracticeLogDetailModal from "@/components/practiceLog/PracticeLogDetailModal";

// 연습일지 목록 화면 — 무한스크롤 목록 + 상세 모달.
// 목록 API가 요약 필드만 주기 때문에, 상세는 카드 선택 시 별도 조회로 채운다(아래 handleSelect).
export default function PracticeLogScreen() {
  const router = useRouter();

  const [logs, setLogs] = useState<PracticeLogSummary[]>([]);
  const [currentPage, setCurrentPage] = useState(0); // 마지막으로 불러온 페이지 번호(0-base)
  const [hasNext, setHasNext] = useState(false); // 다음 페이지 존재 여부 — 무한스크롤 종료 판단
  const [loading, setLoading] = useState(true); // 첫 페이지 로딩 중(전체 화면 스피너)
  const [loadingMore, setLoadingMore] = useState(false); // 다음 페이지 로딩 중(리스트 하단 스피너)
  const [error, setError] = useState<string | null>(null);

  // 상세 모달 상태. selectedId만으로 모달 visible 여부를 결정하고,
  // selectedLog는 조회가 끝나야 채워지므로 그 사이엔 detailLoading으로 스피너를 보여준다.
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [selectedLog, setSelectedLog] = useState<PracticeLogDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // 이 탭이 포커스될 때마다 0페이지부터 다시 조회한다(currentPage/hasNext도 그 응답 기준으로 리셋).
  // 최초 진입은 물론, "작성하기"에서 등록을 마치고 router.back()으로 돌아왔을 때도
  // 이 화면이 다시 포커스를 받으므로 별도의 새로고침 트리거 없이 최신 목록이 반영된다.
  // cancelled 플래그는 포커스가 빠르게 빠졌다가 다시 들어오는 등으로 이전 요청이 늦게 끝났을 때
  // 그 결과로 최신 상태를 덮어쓰지 않기 위한 가드.
  useFocusEffect(
    useCallback(() => {
      let cancelled = false;

      (async () => {
        setLoading(true);
        setError(null);
        try {
          const page = await getPracticeLogs(0);
          if (cancelled) return;
          setLogs(page.content);
          setCurrentPage(page.currentPage);
          setHasNext(page.hasNext);
        } catch (err) {
          if (cancelled) return;
          setError(err instanceof ApiError ? err.message : "연습일지 목록을 불러오지 못했어요.");
        } finally {
          if (!cancelled) setLoading(false);
        }
      })();

      return () => {
        cancelled = true;
      };
    }, [])
  );

  // 무한스크롤 다음 페이지 — FlatList의 onEndReached에서 호출된다.
  // 이미 로딩 중이거나 다음 페이지가 없으면 중복 호출을 막고 바로 반환.
  const loadNextPage = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    try {
      const page = await getPracticeLogs(currentPage + 1);
      setLogs((prev) => [...prev, ...page.content]); // 기존 목록 뒤에 이어붙임(교체 아님)
      setCurrentPage(page.currentPage);
      setHasNext(page.hasNext);
    } catch {
      // 다음 페이지 실패는 조용히 무시 — 이미 보여준 목록은 그대로 유지, 스크롤하면 재시도됨
    } finally {
      setLoadingMore(false);
    }
  };

  // 카드 선택 시 상세 조회. 목록 응답(PracticeLogSummary)에는 content가 없으므로
  // 여기서 GET /api/practice-logs/{id}로 전체 필드를 채워 모달에 넘긴다.
  // selectedId를 먼저 세팅해 모달을 즉시 열고, 조회가 끝날 때까지는 selectedLog가 null이라
  // 모달이 detailLoading을 보고 스피너를 보여준다.
  const handleSelect = async (id: number) => {
    setSelectedId(id);
    setSelectedLog(null);
    setDetailLoading(true);
    try {
      const detail = await getPracticeLog(id);
      setSelectedLog(detail);
    } catch {
      // 상세 조회 실패 시 모달을 열어둔 채 빈 상태로 방치하지 않도록 바로 닫는다
      setSelectedId(null);
    } finally {
      setDetailLoading(false);
    }
  };

  // 수정 화면으로 이동 — 모달을 먼저 닫아, 돌아왔을 때(useFocusEffect 재조회) 이전 모달이
  // 남아 있지 않도록 한다.
  const handleEdit = () => {
    if (selectedId === null) return;
    const id = selectedId;
    setSelectedId(null);
    router.push({ pathname: "/practice-log/[id]/edit", params: { id: String(id) } });
  };

  // 삭제 확인 후 DELETE 호출. 성공하면 목록에서 즉시 제거하고 모달을 닫는다
  // (이 화면이 포커스를 잃지 않으므로 useFocusEffect 재조회에 기대지 않고 직접 state를 갱신).
  const handleDelete = () => {
    if (selectedId === null) return;
    const id = selectedId;
    Alert.alert("연습일지를 삭제할까요?", "삭제하면 되돌릴 수 없어요.", [
      { text: "취소", style: "cancel" },
      {
        text: "삭제",
        style: "destructive",
        onPress: async () => {
          setDeleting(true);
          try {
            await deletePracticeLog(id);
            setLogs((prev) => prev.filter((log) => log.id !== id));
            setSelectedId(null);
          } catch (err) {
            Alert.alert(
              "삭제 실패",
              err instanceof ApiError ? err.message : "연습일지를 삭제하지 못했어요."
            );
          } finally {
            setDeleting(false);
          }
        },
      },
    ]);
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <AppHeader />

      <View style={styles.pageHeader}>
        <Text style={styles.pageTitle}>연습일지</Text>
        <TouchableOpacity
          style={styles.writeButton}
          onPress={() => router.push("/practice-log/create")}
          activeOpacity={0.8}
        >
          <Ionicons name="add" size={16} color={colors.background} />
          <Text style={styles.writeButtonText}>작성하기</Text>
        </TouchableOpacity>
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : error ? (
        <EmptyState icon="cloud-offline-outline" title="목록을 불러오지 못했어요" description={error} />
      ) : (
        <FlatList
          data={logs}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <PracticeLogCard log={item} onPress={() => handleSelect(item.id)} />
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
              icon="musical-notes-outline"
              title="아직 연습 기록이 없어요"
              description="오늘의 연습을 기록해 보세요."
            />
          }
        />
      )}

      <PracticeLogDetailModal
        visible={selectedId !== null}
        log={selectedLog}
        loading={detailLoading}
        onClose={() => setSelectedId(null)}
        onEdit={handleEdit}
        onDelete={handleDelete}
        deleting={deleting}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
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
  writeButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.primary,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
  },
  writeButtonText: {
    fontSize: 13,
    fontWeight: "600",
    color: colors.background,
  },
  listContent: { flexGrow: 1, padding: 16 },
  separator: { height: 12 },
  centerFill: { flex: 1, alignItems: "center", justifyContent: "center" },
  footerSpinner: { marginVertical: 16 },
});
