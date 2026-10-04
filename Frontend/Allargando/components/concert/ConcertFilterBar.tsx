import React from "react";
import { View, ScrollView, TouchableOpacity, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { CONCERT_CATEGORIES } from "@/constants/filterOptions";
import { ConcertFilter } from "@/types/concertFilter";
import Chip from "@/components/common/Chip";

interface ConcertFilterBarProps {
  filter: ConcertFilter;
  onChange: (filter: ConcertFilter) => void;
  onOpenSheet: () => void;
  onReset: () => void;
}

// Post의 FilterBar와 같은 구조지만, 기본 정렬(공연 임박순)이 고정이라 정렬 토글이 없음
export default function ConcertFilterBar({ filter, onChange, onOpenSheet, onReset }: ConcertFilterBarProps) {
  const advancedCount = filter.regions.length + (filter.fromDate || filter.toDate ? 1 : 0);
  const hasAnyFilter = filter.categories.length > 0 || advancedCount > 0;

  function toggleCategory(category: string) {
    const next = filter.categories.includes(category)
      ? filter.categories.filter((c) => c !== category)
      : [...filter.categories, category];
    onChange({ ...filter, categories: next });
  }

  return (
    <View style={styles.wrapper}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.row}
      >
        {CONCERT_CATEGORIES.map((category) => (
          <Chip
            key={category}
            label={category}
            active={filter.categories.includes(category)}
            onPress={() => toggleCategory(category)}
          />
        ))}
      </ScrollView>

      {hasAnyFilter && (
        <TouchableOpacity style={styles.resetButton} onPress={onReset} activeOpacity={0.7}>
          <Ionicons name="close-circle" size={15} color={colors.textMuted} />
        </TouchableOpacity>
      )}

      <TouchableOpacity
        style={[styles.filterButton, advancedCount > 0 && styles.filterButtonActive]}
        onPress={onOpenSheet}
        activeOpacity={0.7}
      >
        <Ionicons
          name="options-outline"
          size={16}
          color={advancedCount > 0 ? colors.background : colors.textSecondary}
        />
        {advancedCount > 0 && (
          <Text style={styles.filterCount}>{advancedCount}</Text>
        )}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flexDirection: "row",
    alignItems: "center",
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
    backgroundColor: colors.background,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 8,
  },
  filterButton: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderLeftWidth: StyleSheet.hairlineWidth,
    borderLeftColor: colors.border,
  },
  filterButtonActive: {
    backgroundColor: colors.primaryLight,
  },
  filterCount: {
    fontSize: 11,
    fontWeight: "700",
    color: colors.background,
  },
  resetButton: {
    paddingHorizontal: 8,
    paddingVertical: 10,
  },
});
