import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";

interface AgreementRowProps {
  label: string;
  checked: boolean;
  onToggle: () => void;
  // 넘기면 오른쪽에 "보기"가 붙어 약관 문서를 열 수 있다
  onView?: () => void;
  // 필수 항목 표시("필수")
  required?: boolean;
}

// 가입 약관 동의 한 줄(체크박스 + 문구 + 문서 보기). 체크박스 영역과 "보기"는 따로 눌린다
export default function AgreementRow({ label, checked, onToggle, onView, required = true }: AgreementRowProps) {
  return (
    <View style={styles.row}>
      <TouchableOpacity
        style={styles.left}
        onPress={onToggle}
        activeOpacity={0.7}
        accessibilityRole="checkbox"
        accessibilityState={{ checked }}
        accessibilityLabel={label}
      >
        <Ionicons
          name={checked ? "checkbox" : "square-outline"}
          size={22}
          color={checked ? colors.primary : colors.placeholder}
        />
        <Text style={styles.label}>
          <Text style={styles.required}>{required ? "[필수] " : "[선택] "}</Text>
          {label}
        </Text>
      </TouchableOpacity>
      {onView && (
        <TouchableOpacity onPress={onView} hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}>
          <Text style={styles.view}>보기</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 8,
  },
  left: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  label: {
    flex: 1,
    fontSize: 13,
    color: colors.textPrimary,
  },
  required: {
    color: colors.textMuted,
  },
  view: {
    fontSize: 12,
    color: colors.textMuted,
    textDecorationLine: "underline",
  },
});
