import React, { useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, StyleProp, ViewStyle } from "react-native";
import DateTimePicker from "@react-native-community/datetimepicker";
import { colors } from "@/constants/theme";
import { formatDate, parseDate, parseTime, toDateOnly, toTimeOnly } from "@/utils/datetime";

interface DateTimeFieldProps {
  mode: "date" | "time";
  label: string;
  // 날짜는 "YYYY-MM-DD", 시간은 "HH:mm". 아직 고르지 않았으면 빈 문자열
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  // 고를 수 있는 날짜 범위(date 모드만)
  minimumDate?: Date;
  maximumDate?: Date;
  style?: StyleProp<ViewStyle>;
}

// 날짜·시간 입력칸. 사용자가 형식을 맞춰 치지 않도록 눌러서 선택기로 고르게 한다.
// 값은 형식이 정해진 문자열로 주고받아 호출하는 쪽이 Date를 다루지 않아도 된다.
// 모집글 작성에서 쓰고, 연습일지 등 다른 날짜 입력도 이 컴포넌트로 옮길 수 있다
export default function DateTimeField({
  mode,
  label,
  value,
  onChange,
  placeholder = mode === "date" ? "날짜 선택" : "시간 선택",
  minimumDate,
  maximumDate,
  style,
}: DateTimeFieldProps) {
  const [pickerVisible, setPickerVisible] = useState(false);

  // 입력칸에 보일 문구와 선택기를 처음 열 때 가리킬 값 — 아직 고르지 않았으면 지금 시각
  const displayValue = value && mode === "date" ? formatDate(value) : value;
  const pickerValue = !value ? new Date() : mode === "date" ? parseDate(value) : parseTime(value);

  return (
    <View style={[styles.container, style]}>
      <Text style={styles.label}>{label}</Text>
      <TouchableOpacity
        style={styles.field}
        onPress={() => setPickerVisible(true)}
        activeOpacity={0.7}
        accessibilityRole="button"
        accessibilityLabel={`${label} ${displayValue || placeholder}`}
      >
        <Text style={[styles.value, !value && styles.placeholder]}>{displayValue || placeholder}</Text>
      </TouchableOpacity>

      {pickerVisible && (
        <DateTimePicker
          value={pickerValue}
          mode={mode}
          is24Hour
          minimumDate={minimumDate}
          maximumDate={maximumDate}
          onChange={(event, selected) => {
            setPickerVisible(false);
            if (event.type === "set" && selected) {
              onChange(mode === "date" ? toDateOnly(selected) : toTimeOnly(selected));
            }
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  // 바깥 여백은 화면마다 달라 호출하는 쪽이 style로 정한다
  container: {
    gap: 8,
  },
  label: {
    fontSize: 12,
    color: colors.textMuted,
    letterSpacing: 1,
  },
  field: {
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  value: {
    fontSize: 14,
    color: colors.textPrimary,
  },
  placeholder: {
    color: colors.placeholder,
  },
});
