import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";
import { PracticeLogSummary } from "@/types/practiceLog";
import { formatDate, formatDuration } from "@/utils/datetime";

interface PracticeLogCardProps {
  log: PracticeLogSummary;
  onPress?: () => void;
}

// 목록 카드 — 날짜·연습 시간·제목만 표시. content는 목록 API 응답에 아예 없으므로
// 이 컴포넌트는 PracticeLogSummary만 받고, 상세(content)는 onPress 이후 부모가 별도 조회한다.
export default function PracticeLogCard({ log, onPress }: PracticeLogCardProps) {
  return (
    <TouchableOpacity style={styles.card} activeOpacity={0.8} onPress={onPress}>
      <View style={styles.topRow}>
        <Text style={styles.date}>{formatDate(log.logDate)}</Text>
        <View style={styles.durationBadge}>
          <Text style={styles.durationText}>{formatDuration(log.duration)}</Text>
        </View>
      </View>
      <Text style={styles.title} numberOfLines={1}>
        {log.title}
      </Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  topRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 6,
  },
  date: {
    fontSize: 12,
    color: colors.textMuted,
  },
  durationBadge: {
    backgroundColor: colors.background,
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  durationText: {
    fontSize: 12,
    color: colors.textSecondary,
    fontWeight: "500",
  },
  title: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.primary,
  },
});
