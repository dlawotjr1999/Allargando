import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";

interface InfoRowProps {
  label: string;
  value: string;
  // 일시처럼 가장 먼저 읽혀야 하는 값이면 크고 굵게 보여 준다
  emphasized?: boolean;
  // 값 오른쪽 끝에 붙일 요소(D-day 등)
  trailing?: React.ReactNode;
}

// 이름표와 값을 나란히 놓는 한 줄. 여러 줄을 쌓으면 값의 시작 위치가 맞고, 긴 값은 잘리지 않고 줄바꿈된다
export default function InfoRow({ label, value, emphasized = false, trailing }: InfoRowProps) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{label}</Text>
      <Text style={[styles.value, emphasized && styles.emphasized]}>{value}</Text>
      {trailing}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "baseline",
    gap: 12,
  },
  label: {
    minWidth: 34,
    fontSize: 13,
    fontWeight: "600",
    color: colors.textSecondary,
  },
  value: {
    flex: 1,
    fontSize: 14,
    lineHeight: 21,
    color: colors.textPrimary,
  },
  emphasized: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: "600",
  },
});
