import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, ActivityIndicator, TouchableOpacity } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useLocalSearchParams, useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { getUserProfile } from "@/api/user";
import { ApiError } from "@/lib/apiClient";
import { confirmBlockUser } from "@/lib/safety";
import { useAuth } from "@/contexts/AuthContext";
import { UserPublicProfile } from "@/types/user";
import { ReportTarget } from "@/types/safety";
import ScreenHeader from "@/components/common/ScreenHeader";
import EmptyState from "@/components/common/EmptyState";
import Tag from "@/components/common/Tag";
import ActionMenu, { ActionMenuItem } from "@/components/common/ActionMenu";
import ReportDialog from "@/components/report/ReportDialog";

// 다른 유저의 공개 프로필 화면(GET /api/users/{nickname}). 닉네임·악기·활동 이력만 보인다 —
// 전화번호·이메일은 서버가 내려주지 않는다. 신고·차단 진입점(앱 내 UGC 정책)도 여기에 둔다.
export default function UserProfileScreen() {
  const { nickname } = useLocalSearchParams<{ nickname: string }>();
  const router = useRouter();
  const { profile: me } = useAuth();

  const [user, setUser] = useState<UserPublicProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [menuVisible, setMenuVisible] = useState(false);
  // 신고 중인 대상(null이면 신고 창 닫힘)
  const [reportTarget, setReportTarget] = useState<ReportTarget | null>(null);

  // 프로필 조회. 없는 닉네임(404)은 탈퇴한 유저일 수 있어 "찾을 수 없음"으로 안내한다
  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUser(await getUserProfile(String(nickname)));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "프로필을 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [nickname]);

  useEffect(() => {
    load();
  }, [load]);

  // 내 프로필이면 신고·차단 메뉴를 숨긴다(닉네임은 대소문자를 구분하지 않고 UNIQUE)
  const isMe = !!user && me?.nickname.toLowerCase() === user.nickname.toLowerCase();

  const menuItems: ActionMenuItem[] = user
    ? [
        { label: "사용자 신고", onPress: () => setReportTarget({ type: "USER", nickname: user.nickname }) },
        {
          label: "사용자 차단",
          // 차단하면 이 유저의 글이 목록에서 사라지므로 성공 후 이전 화면으로 돌아간다
          onPress: () => confirmBlockUser(user.nickname, () => router.back()),
          destructive: true,
        },
      ]
    : [];

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.headerArea}>
        <ScreenHeader
          right={
            user && !isMe ? (
              <TouchableOpacity
                onPress={() => setMenuVisible(true)}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                accessibilityLabel="신고·차단 메뉴"
              >
                <Ionicons name="ellipsis-horizontal" size={22} color={colors.primary} />
              </TouchableOpacity>
            ) : undefined
          }
        />
      </View>

      {loading ? (
        <View style={styles.centerFill}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : !user ? (
        <EmptyState
          icon="person-outline"
          title="프로필을 찾을 수 없어요"
          description={error ?? "탈퇴했거나 존재하지 않는 사용자예요."}
        />
      ) : (
        <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
          <View style={styles.hero}>
            <View style={styles.avatar}>
              <Ionicons name="musical-note" size={34} color={colors.primary} />
            </View>
            <Text style={styles.nickname}>{user.nickname}</Text>
            <View style={styles.tagRow}>
              <Tag label={user.instrument} variant="accent" />
            </View>
          </View>

          <View style={styles.divider} />

          <Text style={styles.sectionTitle}>활동 이력</Text>
          {user.careers.length === 0 ? (
            <Text style={styles.emptyText}>등록된 활동 이력이 없어요.</Text>
          ) : (
            user.careers.map((c) => (
              <View key={c.id} style={styles.careerItem}>
                <View style={styles.careerDot} />
                <View style={styles.careerBody}>
                  {/* 단체명·설명 중 한쪽만 있어도 저장된다 — 빈 쪽은 대시로 보여준다 */}
                  <Text style={styles.careerOrg}>{c.organization || "-"}</Text>
                  <Text style={styles.careerContext}>{c.contexts || "-"}</Text>
                </View>
              </View>
            ))
          )}
        </ScrollView>
      )}

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
  centerFill: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  content: {
    paddingHorizontal: 24,
    paddingBottom: 40,
  },
  hero: {
    alignItems: "center",
    paddingTop: 8,
  },
  avatar: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 14,
  },
  nickname: {
    fontSize: 20,
    fontWeight: "700",
    color: colors.textPrimary,
  },
  tagRow: {
    flexDirection: "row",
    marginTop: 10,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: 24,
  },
  sectionTitle: {
    fontSize: 12,
    color: colors.textMuted,
    letterSpacing: 1,
    marginBottom: 12,
  },
  emptyText: {
    fontSize: 13,
    color: colors.textMuted,
  },
  careerItem: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
    marginBottom: 14,
  },
  careerDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.accent,
    marginTop: 7,
  },
  careerBody: {
    flex: 1,
  },
  careerOrg: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  careerContext: {
    fontSize: 13,
    color: colors.textSecondary,
    marginTop: 2,
    lineHeight: 19,
  },
});
