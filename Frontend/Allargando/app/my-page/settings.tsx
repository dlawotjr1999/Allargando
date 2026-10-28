import React from "react";
import { View, ScrollView, Text, StyleSheet, Alert } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import Constants from "expo-constants";
import { colors } from "@/constants/theme";
import { discardDeviceToken, releasePush, syncPush } from "@/lib/push";
import { usePushSetting } from "@/lib/usePushSetting";
import { deleteMyAccount } from "@/api/user";
import { ApiError } from "@/lib/apiClient";
import { useAuth } from "@/contexts/AuthContext";
import ScreenHeader from "@/components/common/ScreenHeader";
import SettingsSection from "@/components/myPage/SettingsSection";

// 설정 화면 — 마이페이지 상단의 톱니바퀴 버튼으로 들어온다. 푸시 알림·차단 관리·약관과 로그아웃·회원탈퇴, 앱 버전.
// 로그아웃·탈퇴가 끝나면 RootNavigator가 로그인 화면으로 보내므로 여기서 따로 이동하지 않는다
export default function SettingsScreen() {
  const router = useRouter();
  const { signOut } = useAuth();
  const push = usePushSetting();

  // 로그아웃 — 이 기기의 푸시 연결(토큰·새 모집글 토픽)을 먼저 풀어 이전 계정의 알림이 이 기기로 오지 않게 한 뒤,
  // Firebase 세션을 끊으면 RootNavigator가 로그인 화면으로 자동 이동시킨다. 푸시 해제는 실패해도 로그아웃을 막지 않는다
  const handleLogout = async () => {
    await releasePush();
    try {
      await signOut();
    } catch {
      // 로그아웃이 안 됐으면 이 계정이 그대로 쓰이므로 방금 푼 푸시 연결을 되살린다(알림을 끄지 않은 사용자만)
      await syncPush().catch(() => {});
      Alert.alert("로그아웃 실패", "잠시 후 다시 시도해주세요.");
    }
  };

  // 회원탈퇴 — 푸시 토큰 해제 → 서버에서 계정 삭제 → 로그아웃 순서. 서버 삭제에 실패하면(예: 연관 데이터 충돌)
  // 로그인 상태를 유지한다. 서버 삭제가 끝난 뒤의 로그아웃 실패는 탈퇴가 이미 끝난 것이므로 "탈퇴 실패"로 보이지 않게 따로 안내한다.
  const handleWithdraw = async () => {
    await releasePush();
    try {
      await deleteMyAccount();
    } catch (err) {
      if (__DEV__) console.warn("[탈퇴] 서버 삭제 실패", err);
      // 계정이 남아 있으므로 방금 푼 푸시 연결(서버 토큰·새 모집글 토픽)을 되살린다. 사용자가 알림을 꺼 둔 경우는 syncPush가 건너뛴다
      await syncPush().catch(() => {});
      Alert.alert("탈퇴 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
      return;
    }
    // 서버 삭제가 끝났으므로 이 기기의 FCM 토큰도 폐기한다(실패해도 탈퇴는 이미 끝났다)
    await discardDeviceToken();
    try {
      await signOut();
    } catch {
      Alert.alert("탈퇴가 완료되었어요", "계정과 데이터가 삭제되었어요. 앱을 다시 실행하면 로그인 화면으로 돌아갑니다.");
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={["top"]}>
      <View style={styles.headerArea}>
        <ScreenHeader title="설정" />
      </View>
      <ScrollView showsVerticalScrollIndicator={false}>
        <SettingsSection
          pushEnabled={push.enabled}
          pushBusy={push.busy}
          onTogglePush={push.toggle}
          onBlocksPress={() => router.push("/my-page/blocks")}
          onLogout={handleLogout}
          onWithdraw={handleWithdraw}
        />

        <Text style={styles.versionText}>v{Constants.expoConfig?.version ?? ""}</Text>
        <View style={{ height: 40 }} />
      </ScrollView>
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
  versionText: {
    textAlign: "center",
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 16,
  },
});
