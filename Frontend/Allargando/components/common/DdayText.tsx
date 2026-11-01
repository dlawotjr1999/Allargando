import React from "react";
import { Text, StyleSheet, StyleProp, TextStyle } from "react-native";
import { colors } from "@/constants/theme";
import { getDday } from "@/utils/datetime";

interface DdayTextProps {
  date: string;
  style?: StyleProp<TextStyle>;
}

// 날짜까지 남은 날을 D-n으로 표시한다. 임박하면 빨간색, 지났으면 흐리게 보여 주고, 읽을 수 없는 날짜면 그리지 않는다
export default function DdayText({ date, style }: DdayTextProps) {
  const dday = getDday(date);
  if (!dday.label) return null;

  return (
    <Text style={[styles.base, dday.urgent && styles.urgent, dday.expired && styles.expired, style]}>
      {dday.label}
    </Text>
  );
}

const styles = StyleSheet.create({
  base: {
    fontSize: 11,
    fontWeight: "600",
    color: colors.textMuted,
    flexShrink: 0,
  },
  urgent: {
    color: colors.danger,
  },
  expired: {
    color: colors.placeholder,
  },
});
