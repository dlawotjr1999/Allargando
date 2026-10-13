import React, { useCallback, useEffect, useRef, useState } from "react";
import { View, FlatList, StyleSheet, Text, ActivityIndicator, RefreshControl } from "react-native";
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
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 조회 순번 — 필터를 연달아 바꿀 때 늦게 도착한 이전 필터의 응답이 지금 목록을 덮어쓰지 않게 한다(home.tsx와 동일)
  const requestSeq = useRef(0);

  // 필터가 바뀌면 첫 페이지부터 새로 조회. silent가 true면(당겨서 새로고침) 전체 화면 스피너 대신
  // 목록을 그대로 두고 값만 바꾼다
  const loadFirstPage = useCallback(async (silent = false) => {
    const seq = ++requestSeq.current;
    if (!silent) setLoading(true);
    setError(null);
    try {
      const page = await getConcerts(filter, 0);
      if (seq !== requestSeq.current) return; // 그 사이 더 새로운 조회가 시작됨 — 낡은 응답은 버린다
      setConcerts(page.content);
      setCurrentPage(page.currentPage);
      setHasNext(page.hasNext);
    } catch (err) {
      if (seq !== requestSeq.current) return;
      setError(err instanceof ApiError ? err.message : "연주회 목록을 불러오지 못했어요.");
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    loadFirstPage();
  }, [loadFirstPage]);

  // 당겨서 새로고침 — 서버 DB의 최신 목록을 다시 받는다(KOPIS 동기화는 서버가 하루 한 번 하므로 여기서 부르지 않는다)
  const handleRefresh = async () => {
    setRefreshing(true);
    await loadFirstPage(true);
    setRefreshing(false);
  };

  // 무한스크롤 — 다음 페이지를 이어붙임
  const loadNextPage = async () => {
    if (loadingMore || !hasNext) return;
    const seq = requestSeq.current;
    setLoadingMore(true);
    try {
      const page = await getConcerts(filter, currentPage + 1);
      if (seq !== requestSeq.current) return; // 요청 중에 필터가 바뀌어 첫 페이지부터 다시 불러옴 — 이어붙이지 않는다
      // 서버가 정렬을 고정해 두었어도, 스크롤하는 사이 동기화로 목록이 바뀌어 같은 공연이 다음 페이지에 또 올 수 있다.
      // 같은 id가 두 번 들어가면 목록의 key가 겹쳐 오류가 나므로, 이미 가진 공연은 빼고 이어붙인다
      setConcerts((prev) => {
        const known = new Set(prev.map((concert) => concert.id));
        return [...prev, ...page.content.filter((concert) => !known.has(concert.id))];
      });
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
          {/* 서버가 전체 건수를 주지 않아(hasNext만 제공) 불러온 건수만 안다 — 더 있으면 "이상"으로 표시 */}
          <Text style={styles.resultText}>
            {concerts.length}개{hasNext ? " 이상" : ""}
          </Text>
        </View>
      )}

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : error ? (
        <EmptyState
          icon="cloud-offline-outline"
          title="목록을 불러오지 못했어요"
          description={error}
          onRetry={() => loadFirstPage()}
        />
      ) : (
        <FlatList
          data={concerts}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <ConcertCard concert={item} onPress={() => setSelected(item)} />
          )}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} tintColor={colors.primary} />}
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
