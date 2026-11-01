import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";

interface ChipSelectProps {
  label: string;
  options: string[];
  // 하나만 고르면 문자열, 여러 개를 고르는 화면(예: 모집 악기)은 배열로 넘긴다
  selected: string | string[];
  onSelect: (value: string) => void;
}

export default function ChipSelect({
  label,
  options,
  selected,
  onSelect,
}: ChipSelectProps) {
  const isSelected = (option: string) =>
    Array.isArray(selected) ? selected.includes(option) : selected === option;

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.chipRow}>
        {options.map((option) => (
          <TouchableOpacity
            key={option}
            style={[styles.chip, isSelected(option) && styles.chipSelected]}
            onPress={() => onSelect(option)}
          >
            <Text
              style={[
                styles.chipText,
                isSelected(option) && styles.chipTextSelected,
              ]}
            >
              {option}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 14,
  },
  label: {
    fontSize: 12,
    color: colors.textSecondary,
    letterSpacing: 1,
    marginBottom: 6,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  chip: {
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderWidth: 0.5,
    borderColor: colors.border,
    borderRadius: 20,
    backgroundColor: colors.surface,
  },
  chipSelected: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  chipText: {
    fontSize: 13,
    color: colors.textSecondary,
  },
  chipTextSelected: {
    color: colors.background,
  },
});
