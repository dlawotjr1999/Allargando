import React from "react";
import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import ThemedButton from "@/components/common/ThemedButton";

interface ConnectionErrorScreenProps {
  message: string | null;
  retrying: boolean;
  onRetry: () => void;
  onSwitchAccount: () => void;
}

// 로그인은 돼 있는데 서버에서 내 정보를 확인하지 못했을 때(네트워크·서버 장애) 홈 대신 보여 주는 화면.
// 홈으로 보내면 가입 미완료 사용자가 오류만 가득한 홈을 보게 되므로, 확인될 때까지 여기서 다시 시도하게 한다
export default function ConnectionErrorScreen({
  message,
  retrying,
  onRetry,
  onSwitchAccount,
}: ConnectionErrorScreenProps) {
  return (
    <View style={styles.container}>
      <Ionicons name="cloud-offline-outline" size={56} color={colors.textMuted} />
      <Text style={styles.title}>연결을 확인해 주세요</Text>
      <Text style={styles.description}>
        {message ?? "서버에 연결하지 못했어요."} 연결이 되면 다시 시도해 주세요.
      </Text>
      <View style={styles.button}>
        <ThemedButton title="다시 시도" onPress={onRetry} loading={retrying} />
      </View>
      <TouchableOpacity onPress={onSwitchAccount} style={styles.switchAccount}>
        <Text style={styles.switchAccountText}>다른 계정으로 로그인</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 32,
    backgroundColor: colors.background,
  },
  title: {
    fontSize: 18,
    fontWeight: "600",
    color: colors.textPrimary,
    marginTop: 20,
  },
  description: {
    fontSize: 14,
    color: colors.textMuted,
    textAlign: "center",
    lineHeight: 21,
    marginTop: 10,
  },
  button: {
    width: "100%",
    marginTop: 28,
  },
  switchAccount: {
    paddingVertical: 14,
  },
  switchAccountText: {
    fontSize: 13,
    color: colors.textMuted,
    textDecorationLine: "underline",
  },
});
