import React from "react";
import { StyleSheet, Text, TouchableOpacity } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Link } from "expo-router";
import { colors } from "@/constants/theme";
import EmptyState from "@/components/common/EmptyState";

export default function NotFoundScreen() {
  return (
    <SafeAreaView style={styles.container}>
      <EmptyState
        icon="alert-circle-outline"
        title="페이지를 찾을 수 없어요"
        description="요청하신 화면이 존재하지 않거나 삭제되었어요."
      />
      <Link href="/(tabs)/home" asChild>
        <TouchableOpacity style={styles.link} activeOpacity={0.7}>
          <Text style={styles.linkText}>홈으로 돌아가기</Text>
        </TouchableOpacity>
      </Link>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  link: {
    alignSelf: "center",
    marginBottom: 40,
  },
  linkText: {
    fontSize: 14,
    fontWeight: "600",
    color: colors.primary,
  },
});
