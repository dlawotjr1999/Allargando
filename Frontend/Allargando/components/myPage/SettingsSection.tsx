import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert, Switch } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { openLegalDocument, PRIVACY_URL, TERMS_URL } from "@/lib/legal";

// 마이페이지의 설정 목록. 맨 위의 푸시 알림 스위치는 새 모집글·지원 결과 같은 알림을 이 기기에서 받을지를 정한다
// (켜면 알림 권한을 요청하고 서버에 이 기기를 등록, 끄면 등록을 풀고 이 선택을 기기에 저장한다)
interface SettingsSectionProps {
  pushEnabled: boolean;
  // 켜고 끄는 동안(권한 창·서버 호출)에는 스위치를 잠가 연속 조작을 막는다
  pushBusy: boolean;
  onTogglePush: (next: boolean) => void;
  onBlocksPress: () => void;
  onLogout: () => void;
  onWithdraw: () => void;
}

export default function SettingsSection({
  pushEnabled,
  pushBusy,
  onTogglePush,
  onBlocksPress,
  onLogout,
  onWithdraw,
}: SettingsSectionProps) {
  return (
    <View style={styles.settingsSection}>
      <View style={styles.settingsRow}>
        <View style={styles.settingsLeft}>
          <Ionicons name="notifications-outline" size={16} color={colors.textSecondary} />
          <View>
            <Text style={styles.settingsText}>푸시 알림</Text>
            <Text style={styles.settingsHint}>새 모집글과 지원 소식을 알려줘요</Text>
          </View>
        </View>
        <Switch
          value={pushEnabled}
          onValueChange={onTogglePush}
          disabled={pushBusy}
          trackColor={{ true: colors.primary }}
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
  settingsHint: {
    fontSize: 11,
    color: colors.textMuted,
    marginTop: 2,
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
