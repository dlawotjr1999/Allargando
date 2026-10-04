import React from "react";
import { TouchableOpacity, Text, StyleSheet, ViewStyle } from "react-native";
import { colors } from "@/constants/theme";

interface ChipProps {
  label: string;
  active?: boolean;
  onPress: () => void;
  leftIcon?: React.ReactNode;
  style?: ViewStyle;
  // 고를 수 없는 항목(예: 정원이 찬 악기) — 흐리게 보이고 눌러도 반응하지 않는다
  disabled?: boolean;
}

export default function Chip({ label, active = false, onPress, leftIcon, style, disabled = false }: ChipProps) {
  return (
    <TouchableOpacity
      style={[styles.chip, active && styles.chipActive, disabled && styles.chipDisabled, style]}
      onPress={onPress}
      activeOpacity={0.7}
      disabled={disabled}
    >
      {leftIcon}
      <Text style={[styles.text, active && styles.textActive]}>{label}</Text>
    </TouchableOpacity>
  );
}

export const chipTextColor = (active: boolean) =>
  active ? colors.background : colors.textMuted;

const styles = StyleSheet.create({
  chip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  chipActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  text: {
    fontSize: 13,
    color: colors.textSecondary,
  },
  chipDisabled: {
    opacity: 0.4,
  },
  textActive: {
    color: colors.background,
    fontWeight: "600",
  },
});
