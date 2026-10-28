import React from "react";
import { View, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { CONCERT_CATEGORIES, CONCERT_CATEGORY_LABELS } from "@/constants/filterOptions";
import { ConcertFilter } from "@/types/concertFilter";
import { DATE_PRESET_LABELS, formatShortDate, matchPreset } from "@/utils/datetime";
import Chip, { chipTextColor } from "@/components/common/Chip";

interface ConcertFilterBarProps {
  filter: ConcertFilter;
  onChange: (filter: ConcertFilter) => void;
  onOpenRegion: () => void;
  onOpenDate: () => void;
  onReset: () => void;
}

// 지역 칩 문구 — 고른 지역이 없으면 "지역", 하나면 그 이름, 여럿이면 "첫 지역 외 n"
function regionLabel(regions: string[]): string {
  if (regions.length === 0) return "지역";
  return regions.length === 1 ? regions[0] : `${regions[0]} 외 ${regions.length - 1}`;
}

// 날짜 칩 문구 — 빠른 선택과 같으면 그 이름("이번 주말"), 아니면 "MM.DD~MM.DD"(하루면 날짜 하나)
function dateLabel(fromDate?: string, toDate?: string): string {
  const preset = matchPreset(fromDate, toDate);
  if (preset) return DATE_PRESET_LABELS[preset];
  if (fromDate && toDate) {
    return fromDate === toDate ? formatShortDate(fromDate) : `${formatShortDate(fromDate)}~${formatShortDate(toDate)}`;
  }
  if (fromDate) return `${formatShortDate(fromDate)}~`;
  if (toDate) return `~${formatShortDate(toDate)}`;
  return "날짜";
}

// 연주회 필터 바(두 줄). 공연을 고를 때 장르만큼 "언제·어디서"가 중요해 지역·날짜를 시트에 숨기지 않고 늘 보이게 둔다.
// 1줄: 장르 탭(하나만 선택, "전체"는 장르 조건 없음). 서버는 여러 장르를 받지만 3개뿐이라 하나만 고르는 쪽이 단순하다
// 2줄: 지역·날짜(눌러서 시트로 고름, 고른 값이 칩에 보임), "공연중"(진행 중인 공연만 보기 전환), 전체 초기화
export default function ConcertFilterBar({ filter, onChange, onOpenRegion, onOpenDate, onReset }: ConcertFilterBarProps) {
  const selectedCategory = filter.categories[0] ?? null;
  const regionActive = filter.regions.length > 0;
  const dateActive = !!(filter.fromDate || filter.toDate);
  // 초기화 버튼은 검색어까지 함께 지우므로 검색 중일 때도 보인다
  const hasAnyFilter =
    filter.running || filter.categories.length > 0 || regionActive || dateActive || filter.keyword.length > 0;

  // ▾ 아이콘 — 칩이 켜져 있으면 글자와 같은 색으로 바꾼다
  const chevron = (active: boolean) => <Ionicons name="chevron-down" size={12} color={chipTextColor(active)} />;

  return (
    <View style={styles.wrapper}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
        <Chip
          label="전체"
          active={selectedCategory === null}
          onPress={() => onChange({ ...filter, categories: [] })}
        />
        {CONCERT_CATEGORIES.map((category) => (
          <Chip
            key={category}
            label={CONCERT_CATEGORY_LABELS[category] ?? category}
            active={selectedCategory === category}
            onPress={() => onChange({ ...filter, categories: [category] })}
          />
        ))}
      </ScrollView>

      <View style={styles.secondRow}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
          <Chip
            label={regionLabel(filter.regions)}
            active={regionActive}
            onPress={onOpenRegion}
            rightIcon={chevron(regionActive)}
          />
          <Chip
            label={dateLabel(filter.fromDate, filter.toDate)}
            active={dateActive}
            onPress={onOpenDate}
            rightIcon={chevron(dateActive)}
          />
          <View style={styles.divider} />
          <Chip
            label="공연중"
            active={filter.running}
            onPress={() => onChange({ ...filter, running: !filter.running })}
          />
        </ScrollView>

        {hasAnyFilter && (
          <TouchableOpacity
            style={styles.resetButton}
            onPress={onReset}
            activeOpacity={0.7}
            accessibilityLabel="필터 초기화"
          >
            <Ionicons name="close-circle" size={15} color={colors.textMuted} />
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
    backgroundColor: colors.background,
    paddingVertical: 4,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 6,
    gap: 8,
  },
  secondRow: {
    flexDirection: "row",
    alignItems: "center",
  },
  divider: {
    width: StyleSheet.hairlineWidth,
    height: 18,
    backgroundColor: colors.border,
  },
  resetButton: {
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
});
