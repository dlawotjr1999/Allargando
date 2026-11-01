import React from "react";
import { Text, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";

interface TagProps {
  label: string;
  variant?: "filled" | "outline" | "accent" | "muted";
  // 기본(12px)보다 내용을 읽어야 하는 칩이면 true(13px)
  large?: boolean;
}

// 읽기 전용 라벨 칩. 카테고리 뱃지·악기 표시 등에 재사용.
// accent는 벚꽃 분홍 바탕의 강조 뱃지(카테고리 등), muted는 마감처럼 끝난 항목을 흐리게 보이는 칩.
// (선택 가능한 칩은 ChipSelect를 사용)
export default function Tag({ label, variant = "outline", large = false }: TagProps) {
  return (
    <Text style={[styles.base, large && styles.large, styles[variant]]}>
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
  large: {
    fontSize: 13,
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
  muted: {
    borderWidth: 0.5,
    borderColor: colors.border,
    backgroundColor: colors.background,
    color: colors.textMuted,
  },
});
