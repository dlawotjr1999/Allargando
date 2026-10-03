import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert, Switch } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";

interface SettingsSectionProps {
  notifEnabled: boolean;
  onToggleNotif: (value: boolean) => void;
  onBlocksPress: () => void;
  onLogout: () => void;
  onWithdraw: () => void;
}

export default function SettingsSection({
  notifEnabled,
  onToggleNotif,
  onBlocksPress,
  onLogout,
  onWithdraw,
}: SettingsSectionProps) {
  const handleComingSoon = () =>
    Alert.alert("준비 중입니다", "곧 추가될 예정이에요.");

  return (
    <View style={styles.settingsSection}>
      <View style={styles.settingsRow}>
        <View style={styles.settingsLeft}>
          <Ionicons name="notifications-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.settingsText}>모집 알림</Text>
        </View>
        <Switch
          value={notifEnabled}
          onValueChange={onToggleNotif}
          trackColor={{ false: colors.border, true: colors.primaryLight }}
          thumbColor={notifEnabled ? colors.primary : colors.placeholder}
        />
      </View>
      <View style={styles.divider} />

      <TouchableOpacity style={styles.settingsRow} onPress={onBlocksPress} activeOpacity={0.7}>
        <View style={styles.settingsLeft}>
          <Ionicons name="ban-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.settingsText}>차단 관리</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
      </TouchableOpacity>
      <View style={styles.divider} />

      <TouchableOpacity style={styles.settingsRow} onPress={handleComingSoon} activeOpacity={0.7}>
        <View style={styles.settingsLeft}>
          <Ionicons name="document-text-outline" size={16} color={colors.textSecondary} />
          <Text style={styles.settingsText}>이용약관</Text>
        </View>
        <Ionicons name="chevron-forward" size={16} color={colors.textMuted} />
      </TouchableOpacity>
      <View style={styles.divider} />

      <TouchableOpacity style={styles.settingsRow} onPress={handleComingSoon} activeOpacity={0.7}>
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
          Alert.alert("회원탈퇴", "정말 탈퇴하시겠어요?", [
            { text: "취소", style: "cancel" },
            { text: "탈퇴", style: "destructive", onPress: onWithdraw },
          ])
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
