import React from "react";
import { Text, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";

interface TagProps {
  label: string;
  variant?: "filled" | "outline" | "accent";
}

// 읽기 전용 라벨 칩. 카테고리 뱃지·악기 표시 등에 재사용.
// accent는 벚꽃 분홍 바탕의 강조 뱃지(카테고리 등). (선택 가능한 칩은 ChipSelect를 사용)
export default function Tag({ label, variant = "outline" }: TagProps) {
  return (
    <Text style={[styles.base, styles[variant]]}>
      {label}
    </Text>
  );
}

const styles = StyleSheet.create({
  base: {
    paddingVertical: 5,
    paddingHorizontal: 12,
    borderRadius: 16,
    fontSize: 12,
    overflow: "hidden",
  },
  outline: {
    borderWidth: 0.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    color: colors.textSecondary,
  },
  filled: {
    backgroundColor: colors.primary,
    color: colors.background,
  },
  accent: {
    backgroundColor: colors.accent,
    color: colors.primary,
    fontWeight: "600",
  },
});
