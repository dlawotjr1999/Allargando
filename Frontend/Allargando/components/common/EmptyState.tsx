import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import ThemedButton from "@/components/common/ThemedButton";

interface EmptyStateProps {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  description?: string;
  // 오류 화면에서 다시 시도할 수단. 있으면 설명 아래에 "다시 시도" 버튼을 보여 준다
  onRetry?: () => void;
}

export default function EmptyState({
  icon,
  title,
  description,
  onRetry,
}: EmptyStateProps) {
  return (
    <View style={styles.container}>
      <Ionicons name={icon} size={48} color={colors.textMuted} />
      <Text style={styles.title}>{title}</Text>
      {description && <Text style={styles.description}>{description}</Text>}
      {onRetry && (
        <View style={styles.retry}>
          <ThemedButton title="다시 시도" variant="outline" onPress={onRetry} />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 32,
  },
  title: {
    fontSize: 15,
    fontWeight: "500",
    color: colors.textSecondary,
    marginTop: 16,
    textAlign: "center",
  },
  retry: {
    marginTop: 20,
    minWidth: 140,
  },
  description: {
    fontSize: 13,
    color: colors.textMuted,
    marginTop: 6,
    textAlign: "center",
    lineHeight: 19,
  },
});
