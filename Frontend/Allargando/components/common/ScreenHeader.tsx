import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";

interface ScreenHeaderProps {
  title?: string;
  subtitle?: string;
  showBack?: boolean;
  // 뒤로가기 버튼과 같은 줄 오른쪽 끝에 놓을 요소(예: 더보기 버튼)
  right?: React.ReactNode;
}

export default function ScreenHeader({
  title,
  subtitle,
  showBack = true,
  right,
}: ScreenHeaderProps) {
  const router = useRouter();

  return (
    <View style={styles.container}>
      {(showBack || right) && (
        <View style={styles.topRow}>
          {showBack ? (
            <TouchableOpacity
              onPress={() => router.back()}
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              accessibilityLabel="뒤로 가기"
            >
              <Ionicons name="arrow-back" size={22} color={colors.primary} />
            </TouchableOpacity>
          ) : (
            <View />
          )}
          {right}
        </View>
      )}
      {title ? <Text style={styles.title}>{title}</Text> : null}
      {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 28,
  },
  topRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 20,
  },
  title: {
    fontSize: 20,
    fontWeight: "500",
    color: colors.primary,
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 13,
    color: colors.textMuted,
  },
});
