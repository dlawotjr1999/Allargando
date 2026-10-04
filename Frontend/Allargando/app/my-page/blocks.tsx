import React, { useCallback, useEffect, useState } from "react";
import { View, Text, FlatList, StyleSheet, ActivityIndicator, Alert } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { colors } from "@/constants/theme";
import { getMyBlocks, unblockUser } from "@/api/block";
import { ApiError } from "@/lib/apiClient";
import { markPostListStale } from "@/lib/postListRefresh";
import { BlockedUser } from "@/types/safety";
import ScreenHeader from "@/components/common/ScreenHeader";
import EmptyState from "@/components/common/EmptyState";
import ThemedButton from "@/components/common/ThemedButton";

// 차단 관리 화면 — 내가 차단한 유저 목록과 차단 해제. 차단을 풀면 그 유저의 모집글이 목록에 다시 보이므로
// 모집글 목록이 다음에 포커스될 때 다시 조회하도록 표시를 남긴다.
export default function BlocksScreen() {
  const [blocks, setBlocks] = useState<BlockedUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // 해제 중인 닉네임 — 해당 행의 버튼만 잠가 연속 탭을 막는다
  const [processingNickname, setProcessingNickname] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setBlocks(await getMyBlocks());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "차단 목록을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // 차단 해제. 이미 해제된 상태(404)도 목적은 달성된 것이라 목록에서 지운다
  const handleUnblock = async (nickname: string) => {
    if (processingNickname) return;
    setProcessingNickname(nickname);
    try {
      await unblockUser(nickname);
      setBlocks((prev) => prev.filter((b) => b.nickname !== nickname));
      markPostListStale();
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setBlocks((prev) => prev.filter((b) => b.nickname !== nickname));
      } else {
        Alert.alert("해제 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
      }
    } finally {
      setProcessingNickname(null);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.headerArea}>
        <ScreenHeader />
        <Text style={styles.headerTitle}>차단 관리</Text>
        <Text style={styles.headerSubtitle}>차단한 사용자의 모집글은 목록에 보이지 않아요.</Text>
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : error ? (
        <EmptyState icon="cloud-offline-outline" title="목록을 불러오지 못했어요" description={error} />
      ) : (
        <FlatList
          data={blocks}
          keyExtractor={(item) => item.nickname}
          contentContainerStyle={styles.listContent}
          ItemSeparatorComponent={() => <View style={styles.separator} />}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <View style={styles.rowInfo}>
                <Text style={styles.nickname} numberOfLines={1}>
                  {item.nickname}
                </Text>
                <Text style={styles.instrument}>{item.instrument}</Text>
              </View>
              <ThemedButton
                title="해제"
                variant="outline"
                onPress={() => handleUnblock(item.nickname)}
                loading={processingNickname === item.nickname}
                style={styles.unblockButton}
              />
            </View>
          )}
          ListEmptyComponent={
            <EmptyState
              icon="happy-outline"
              title="차단한 사용자가 없어요"
              description="모집글이나 지원자 화면에서 사용자를 차단할 수 있어요."
            />
          }
        />
      )}
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
  headerTitle: {
    fontSize: 20,
    fontWeight: "500",
    color: colors.primary,
    marginBottom: 6,
  },
  headerSubtitle: {
    fontSize: 13,
    color: colors.textMuted,
    marginBottom: 16,
  },
  centerFill: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  listContent: {
    paddingHorizontal: 24,
    paddingBottom: 40,
  },
  separator: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 12,
  },
  rowInfo: {
    flex: 1,
    marginRight: 12,
  },
  nickname: {
    fontSize: 15,
    color: colors.textPrimary,
  },
  instrument: {
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 2,
  },
  unblockButton: {
    height: 36,
    paddingHorizontal: 18,
  },
});
