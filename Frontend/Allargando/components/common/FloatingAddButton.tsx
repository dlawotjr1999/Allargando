import React from "react";
import { TouchableOpacity, Text, StyleSheet } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors } from "@/constants/theme";

interface FloatingAddButtonProps {
  onPress: () => void;
  // 스크린리더가 읽을 버튼 이름(예: "모집글 작성")
  accessibilityLabel: string;
}

// 목록 탭 오른쪽 아래에 떠 있는 "+" 작성 버튼. 탭마다 위치·모양이 같도록 모집글·연습일지 목록이 함께 쓴다.
// 부모(SafeAreaView)를 기준으로 절대 위치를 잡으므로 화면 최상위 컨테이너의 마지막 자식으로 둘 것
export default function FloatingAddButton({ onPress, accessibilityLabel }: FloatingAddButtonProps) {
  const insets = useSafeAreaInsets();

  return (
    <TouchableOpacity
      style={[styles.fab, { bottom: insets.bottom }]}
      onPress={onPress}
      activeOpacity={0.7}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
    >
      <Text style={styles.fabText}>+</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  fab: {
    position: "absolute",
    right: 24,
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
    opacity: 0.8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 4,
  },
  fabText: {
    fontSize: 26,
    color: colors.background,
    lineHeight: 30,
  },
});
