import React, { useCallback, useEffect, useState } from "react";
import { View, FlatList, StyleSheet, TouchableOpacity, Text, ActivityIndicator } from "react-native";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { useFocusEffect } from "@react-navigation/native";
import { colors } from "@/constants/theme";
import { getPosts } from "@/api/post";
import { ApiError } from "@/lib/apiClient";
import { consumePostListStale } from "@/lib/postListRefresh";
import { PostSummary } from "@/types/post";
import { PostFilter, DEFAULT_FILTER } from "@/types/filter";
import AppHeader from "@/components/common/AppHeader";
import EmptyState from "@/components/common/EmptyState";
import PostCard from "@/components/post/PostCard";
import FilterBar from "@/components/post/FilterBar";
import FilterSheet from "@/components/post/FilterSheet";

// 모집글 목록 화면 — 필터(카테고리·악기·지역·기간) + 무한스크롤 목록 + FAB(등록).
// 필터가 바뀌면 loadFirstPage가 재실행되어 0페이지부터 다시 조회한다(아래 useEffect 의존성 참고).
//
// filter.sort("최신순" 토글)와 filter.status(상태 칩)는 이 화면에서 UI로는 남아있지만 서버 쿼리에는
// 반영되지 않는다 — api/post.ts의 buildQuery 주석 참고: 목록은 항상 createdAt DESC 고정이고,
// 공개 목록은 항상 OPEN·PARTIALLY_CLOSED만 노출(CLOSED 선택 자체가 서버에서 무의미)되기 때문이다.
export default function ObriScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [filter, setFilter] = useState<PostFilter>(DEFAULT_FILTER);
  const [sheetVisible, setSheetVisible] = useState(false);

  const [posts, setPosts] = useState<PostSummary[]>([]);
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
      const page = await getPosts(filter, 0);
      setPosts(page.content);
      setCurrentPage(page.currentPage);
      setHasNext(page.hasNext);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "모집글 목록을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    loadFirstPage();
  }, [loadFirstPage]);

  // 다른 화면에서 모집글을 등록·수정·삭제하거나 유저를 차단·해제했다면(lib/postListRefresh) 돌아왔을 때 다시 조회.
  // 변경이 없을 땐 아무것도 하지 않아 스크롤 위치가 유지된다
  useFocusEffect(
    useCallback(() => {
      if (consumePostListStale()) loadFirstPage();
    }, [loadFirstPage])
  );

  // 무한스크롤 — 다음 페이지를 이어붙임
  const loadNextPage = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    try {
      const page = await getPosts(filter, currentPage + 1);
      setPosts((prev) => [...prev, ...page.content]);
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

      <FilterBar
        filter={filter}
        onChange={setFilter}
        onOpenSheet={() => setSheetVisible(true)}
        onReset={() => setFilter(DEFAULT_FILTER)}
      />

      {!loading && !error && (
        <View style={styles.resultRow}>
          <Text style={styles.resultText}>총 {posts.length}개</Text>
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
          data={posts}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <PostCard
              post={item}
              onPress={(id) => router.push({ pathname: "/post/[id]", params: { id } })}
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
              icon="document-text-outline"
              title="조건에 맞는 모집글이 없어요"
              description="필터를 조정하거나 나중에 다시 확인해 주세요."
            />
          }
        />
      )}

      <FilterSheet
        visible={sheetVisible}
        filter={filter}
        onApply={setFilter}
        onClose={() => setSheetVisible(false)}
      />

      {/* FAB */}
      <TouchableOpacity
        style={[styles.fab, { bottom: insets.bottom }]}
        onPress={() => router.push("/post/create")}
        activeOpacity={0.7}
      >
        <Text style={styles.fabText}>+</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  resultRow: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 4,
  },
  resultText: {
    fontSize: 12,
    color: colors.textMuted,
  },
  listContent: {
    flexGrow: 1,
    padding: 16,
  },
  separator: {
    height: 12,
  },
  centerFill: { flex: 1, alignItems: "center", justifyContent: "center" },
  footerSpinner: { marginVertical: 16 },
  fab: {
    position: "absolute",
    right: 24,
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
    opacity: 0.8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 4,
  },
  fabText: {
    fontSize: 26,
    color: colors.background,
    lineHeight: 30,
  },
});
