import React from "react";
import { View, Text, StyleSheet, Linking } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { StatusBar } from "expo-status-bar";
import { colors } from "@/constants/theme";
import ThemedButton from "@/components/common/ThemedButton";

const PACKAGE_NAME = "com.wangnu.allargando";

// 서버가 이 앱 버전을 더 지원하지 않는다고 알렸을 때(426) 앱 전체를 대신하는 화면. 로그아웃시키지 않고 스토어로만 보낸다.
// Play 스토어 앱이 없는 기기를 위해 market:// 이 실패하면 웹 주소로 연다
export default function UpgradeRequiredScreen() {
  const openStore = async () => {
    try {
      await Linking.openURL(`market://details?id=${PACKAGE_NAME}`);
    } catch {
      await Linking.openURL(`https://play.google.com/store/apps/details?id=${PACKAGE_NAME}`).catch(() => {});
    }
  };

  return (
    <View style={styles.container}>
      <Ionicons name="cloud-download-outline" size={56} color={colors.primary} />
      <Text style={styles.title}>업데이트가 필요해요</Text>
      <Text style={styles.description}>
        이 버전은 더 이상 사용할 수 없어요. 최신 버전으로 업데이트한 뒤 다시 이용해 주세요.
      </Text>
      <View style={styles.button}>
        <ThemedButton title="업데이트하러 가기" onPress={openStore} />
      </View>
      <StatusBar style="dark" />
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
});
