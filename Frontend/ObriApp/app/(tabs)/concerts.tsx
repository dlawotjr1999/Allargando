import React, { useCallback, useEffect, useState } from "react";
import { View, FlatList, StyleSheet, Text, ActivityIndicator } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors } from "@/constants/theme";
import { getConcerts } from "@/api/concert";
import { ApiError } from "@/lib/apiClient";
import { Concert } from "@/types/concert";
import { ConcertFilter, DEFAULT_CONCERT_FILTER } from "@/types/concertFilter";
import AppHeader from "@/components/common/AppHeader";
import EmptyState from "@/components/common/EmptyState";
import ConcertCard from "@/components/concert/ConcertCard";
import ConcertDetailModal from "@/components/concert/ConcertDetailModal";
import ConcertFilterBar from "@/components/concert/ConcertFilterBar";
import ConcertFilterSheet from "@/components/concert/ConcertFilterSheet";

// 연주회 목록 화면 — 필터(카테고리·지역·기간) + 무한스크롤 목록 + 상세 모달.
// 필터가 바뀌면 loadFirstPage가 재실행되어 0페이지부터 다시 조회한다(아래 useEffect 의존성 참고).
export default function ConcertsScreen() {
  const [selected, setSelected] = useState<Concert | null>(null);
  const [filter, setFilter] = useState<ConcertFilter>(DEFAULT_CONCERT_FILTER);
  const [sheetVisible, setSheetVisible] = useState(false);

  const [concerts, setConcerts] = useState<Concert[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 필터가 바뀌면 첫 페이지부터 새로 조회
  const loadFirstPage = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await getConcerts(filter, 0);
      setConcerts(page.content);
      setCurrentPage(page.currentPage);
      setHasNext(page.hasNext);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "연주회 목록을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    loadFirstPage();
  }, [loadFirstPage]);

  // 무한스크롤 — 다음 페이지를 이어붙임
  const loadNextPage = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    try {
      const page = await getConcerts(filter, currentPage + 1);
      setConcerts((prev) => [...prev, ...page.content]);
      setCurrentPage(page.currentPage);
      setHasNext(page.hasNext);
    } catch {
      // 다음 페이지 실패는 조용히 무시 — 이미 보여준 목록은 그대로 유지, 스크롤하면 재시도됨
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <AppHeader />

      <ConcertFilterBar
        filter={filter}
        onChange={setFilter}
        onOpenSheet={() => setSheetVisible(true)}
        onReset={() => setFilter(DEFAULT_CONCERT_FILTER)}
      />

      {!loading && !error && (
        <View style={styles.resultRow}>
          <Text style={styles.resultText}>총 {concerts.length}개</Text>
        </View>
      )}

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : error ? (
        <EmptyState icon="cloud-offline-outline" title="목록을 불러오지 못했어요" description={error} />
      ) : (
        <FlatList
          data={concerts}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <ConcertCard concert={item} onPress={() => setSelected(item)} />
          )}
          contentContainerStyle={styles.listContent}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          onEndReached={loadNextPage}
          onEndReachedThreshold={0.4}
          ListFooterComponent={
            <>
              {loadingMore && <ActivityIndicator style={styles.footerSpinner} color={colors.primary} />}
              {/* 공공 API 데이터 출처 표기 */}
              <Text style={styles.sourceText}>공연 정보 출처: 공연예술통합전산망(KOPIS)</Text>
            </>
          }
          ListEmptyComponent={
            <EmptyState
              icon="musical-notes-outline"
              title="조건에 맞는 연주회가 없어요"
              description="필터를 조정하거나 나중에 다시 확인해 주세요."
            />
          }
        />
      )}

      <ConcertFilterSheet
        visible={sheetVisible}
        filter={filter}
        onApply={setFilter}
        onClose={() => setSheetVisible(false)}
      />

      <ConcertDetailModal concert={selected} onClose={() => setSelected(null)} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  resultRow: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 4,
  },
  resultText: {
    fontSize: 12,
    color: colors.textMuted,
  },
  listContent: { flexGrow: 1, padding: 16 },
  separator: { height: 12 },
  centerFill: { flex: 1, alignItems: "center", justifyContent: "center" },
  footerSpinner: { marginVertical: 16 },
  sourceText: {
    marginTop: 16,
    fontSize: 11,
    color: colors.textMuted,
    textAlign: "center",
  },
});
