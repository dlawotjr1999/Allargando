import React from "react";
import { View, Text, TouchableOpacity, Pressable, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";

export interface ActionMenuItem {
  label: string;
  onPress: () => void;
  // 삭제·차단처럼 되돌리기 어려운 동작은 빨간색으로 표시
  destructive?: boolean;
}

interface ActionMenuProps {
  visible: boolean;
  items: ActionMenuItem[];
  onDismiss: () => void;
}

// 화면 오른쪽 위 더보기(⋯) 버튼에서 여는 팝오버 메뉴. Modal을 쓰지 않고 화면 위에 겹쳐 그리므로
// 항목을 누른 직후 확인 Alert이나 다른 Modal을 바로 띄워도 겹침 문제가 없다.
// 부모(상대 위치 컨테이너)의 오른쪽 위에 붙는다.
export default function ActionMenu({ visible, items, onDismiss }: ActionMenuProps) {
  if (!visible) return null;

  // 항목을 누르면 메뉴를 먼저 닫고 해당 동작을 실행
  const run = (action: () => void) => () => {
    onDismiss();
    action();
  };

  return (
    <>
      <Pressable style={StyleSheet.absoluteFill} onPress={onDismiss} accessibilityLabel="메뉴 닫기" />
      <View style={styles.menu}>
        {items.map((item) => (
          <TouchableOpacity
            key={item.label}
            style={styles.item}
            onPress={run(item.onPress)}
            activeOpacity={0.7}
          >
            <Text style={[styles.itemText, item.destructive && styles.danger]}>{item.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  menu: {
    position: "absolute",
    top: 44,
    right: 24,
    minWidth: 140,
    backgroundColor: colors.surface,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    paddingVertical: 4,
    elevation: 6,
    shadowColor: "#000",
    shadowOpacity: 0.12,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
  },
  item: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  itemText: {
    fontSize: 14,
    color: colors.textPrimary,
  },
  danger: {
    color: "#C0392B",
  },
});
