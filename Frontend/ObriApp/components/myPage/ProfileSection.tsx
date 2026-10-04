import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { UserProfile } from "@/types/user";

interface ProfileSectionProps {
  user: UserProfile;
  myPostCount: number;
  totalApplications: number;
  acceptedApplications: number;
  onEditPress: () => void;
}

export default function ProfileSection({
  user,
  myPostCount,
  totalApplications,
  acceptedApplications,
  onEditPress,
}: ProfileSectionProps) {
  return (
    <View style={styles.profileSection}>
      <View style={styles.profileRow}>
        <View style={styles.avatar}>
          <Ionicons name="musical-note" size={28} color={colors.primary} />
        </View>
        <View style={styles.profileInfo}>
          <Text style={styles.nickname}>{user.nickname}</Text>
          <Text style={styles.profileMeta}>{user.instrument}</Text>
        </View>
        <TouchableOpacity style={styles.editButton} onPress={onEditPress} activeOpacity={0.7}>
          <Ionicons name="pencil-outline" size={16} color={colors.textSecondary} />
        </TouchableOpacity>
      </View>

      <View style={styles.statsRow}>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>{myPostCount}</Text>
          <Text style={styles.statLabel}>내 모집글</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statItem}>
          <Text style={styles.statValue}>{totalApplications}</Text>
          <Text style={styles.statLabel}>총 지원</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statItem}>
          <Text style={styles.statValue}>{acceptedApplications}</Text>
          <Text style={styles.statLabel}>수락됨</Text>
        </View>
      </View>

      {user.careers.length > 0 && (
        <View style={styles.careerSection}>
          <Text style={styles.careerLabel}>활동 이력</Text>
          {user.careers.map((c) => (
            <View key={c.id} style={styles.careerItem}>
              <View style={styles.careerDot} />
              <View style={{ flex: 1 }}>
                {/* 활동 이력은 단체명·설명 중 한쪽만 있어도 저장된다(혼자 연주한 경우 등) — 빈 쪽은 대시로 보여준다 */}
                <Text style={styles.careerOrg}>{c.organization || "-"}</Text>
                <Text style={styles.careerContext}>{c.contexts || "-"}</Text>
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  profileSection: {
    backgroundColor: colors.surface,
    margin: 16,
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    padding: 16,
  },
  profileRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
  },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.background,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  profileInfo: {
    flex: 1,
    gap: 4,
  },
  nickname: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.textPrimary,
  },
  profileMeta: {
    fontSize: 13,
    color: colors.textMuted,
  },
  editButton: {
    padding: 6,
  },
  statsRow: {
    flexDirection: "row",
    marginTop: 16,
    paddingTop: 14,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
  statItem: {
    flex: 1,
    alignItems: "center",
    gap: 3,
  },
  statValue: {
    fontSize: 18,
    fontWeight: "700",
    color: colors.textPrimary,
  },
  statLabel: {
    fontSize: 11,
    color: colors.textMuted,
  },
  statDivider: {
    width: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: 4,
  },
  careerSection: {
    marginTop: 16,
    paddingTop: 14,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    gap: 10,
  },
  careerLabel: {
    fontSize: 12,
    fontWeight: "600",
    color: colors.textMuted,
    letterSpacing: 0.5,
    marginBottom: 2,
  },
  careerItem: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
  },
  careerDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: colors.primaryLight,
    marginTop: 5,
  },
  careerOrg: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  careerContext: {
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 1,
  },
});
