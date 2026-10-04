import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { openLegalDocument, PRIVACY_URL, TERMS_URL } from "@/lib/legal";

// 설정 목록. "모집 알림" 스위치는 푸시 수신(토큰 등록)이 연동될 때 함께 추가한다 — 지금은 동작하지 않는
// 스위치를 두지 않는다(스토어 심사의 "죽은 버튼 없음" 조건)
interface SettingsSectionProps {
  onBlocksPress: () => void;
  onLogout: () => void;
  onWithdraw: () => void;
}

export default function SettingsSection({
  onBlocksPress,
  onLogout,
  onWithdraw,
}: SettingsSectionProps) {
  return (
    <View style={styles.settingsSection}>
      <TouchableOpacity style={styles.settingsRow} onPress={onBlocksPress} activeOpacity={0.7}>
        <View style={styles.settingsLeft}>
          <Ionicons name="ban-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.settingsText}>차단 관리</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
      </TouchableOpacity>
      <View style={styles.divider} />

      <TouchableOpacity
        style={styles.settingsRow}
        onPress={() => openLegalDocument("이용약관", TERMS_URL)}
        activeOpacity={0.7}
      >
        <View style={styles.settingsLeft}>
          <Ionicons name="document-text-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.settingsText}>이용약관</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
      </TouchableOpacity>
      <View style={styles.divider} />

      <TouchableOpacity
        style={styles.settingsRow}
        onPress={() => openLegalDocument("개인정보처리방침", PRIVACY_URL)}
        activeOpacity={0.7}
      >
        <View style={styles.settingsLeft}>
          <Ionicons name="lock-closed-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.settingsText}>개인정보처리방침</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
      </TouchableOpacity>
      <View style={styles.divider} />

      <TouchableOpacity
        style={styles.settingsRow}
        onPress={() =>
          Alert.alert("로그아웃", "로그아웃하시겠어요?", [
            { text: "취소", style: "cancel" },
            { text: "로그아웃", style: "destructive", onPress: onLogout },
          ])
        }
        activeOpacity={0.7}
      >
        <View style={styles.settingsLeft}>
          <Ionicons name="log-out-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.settingsText}>로그아웃</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
      </TouchableOpacity>
      <View style={styles.divider} />

      <TouchableOpacity
        style={styles.settingsRow}
        onPress={() =>
          Alert.alert(
            "회원탈퇴",
            "탈퇴하면 내가 올린 모집글(받은 지원 포함), 내가 낸 지원 내역, 연습일지, 프로필 정보가 모두 삭제되고 되돌릴 수 없어요.\n\n정말 탈퇴하시겠어요?",
            [
              { text: "취소", style: "cancel" },
              { text: "탈퇴", style: "destructive", onPress: onWithdraw },
            ]
          )
        }
        activeOpacity={0.7}
      >
        <View style={styles.settingsLeft}>
          <Ionicons name="person-remove-outline" size={16} color={colors.danger} />
          <Text style={[styles.settingsText, styles.settingsDanger]}>회원탈퇴</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  settingsSection: {
    marginHorizontal: 16,
    marginTop: 8,
    backgroundColor: colors.surface,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    overflow: "hidden",
  },
  settingsRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 13,
  },
  settingsLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  settingsText: {
    fontSize: 14,
    color: colors.textSecondary,
  },
  settingsDanger: {
    color: colors.danger,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginHorizontal: 16,
  },
});
