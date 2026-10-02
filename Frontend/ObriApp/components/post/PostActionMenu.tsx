import React from "react";
import { View, Text, TouchableOpacity, Pressable, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";

interface PostActionMenuProps {
  visible: boolean;
  // 이미 마감된 글이면 "모집 마감"을 숨긴다
  canClose: boolean;
  onEdit: () => void;
  onCloseRecruit: () => void;
  onDelete: () => void;
  onDismiss: () => void;
}

// 내 모집글 상세의 더보기(⋯) 메뉴 — 수정 / 모집 마감 / 삭제. Modal을 쓰지 않고 화면 위에 겹쳐 그리는 팝오버라
// 항목을 누른 직후 확인 Alert을 바로 띄워도 iOS에서 Modal 닫힘과 겹쳐 무시되는 문제가 없다.
// 부모(상대 위치 컨테이너)의 오른쪽 위에 붙는다.
export default function PostActionMenu({
  visible,
  canClose,
  onEdit,
  onCloseRecruit,
  onDelete,
  onDismiss,
}: PostActionMenuProps) {
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
        <TouchableOpacity style={styles.item} onPress={run(onEdit)} activeOpacity={0.7}>
          <Text style={styles.itemText}>수정</Text>
        </TouchableOpacity>
        {canClose && (
          <TouchableOpacity style={styles.item} onPress={run(onCloseRecruit)} activeOpacity={0.7}>
            <Text style={styles.itemText}>모집 마감</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity style={styles.item} onPress={run(onDelete)} activeOpacity={0.7}>
          <Text style={[styles.itemText, styles.danger]}>삭제</Text>
        </TouchableOpacity>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  menu: {
    position: "absolute",
    top: 44,
    right: 24,
    minWidth: 130,
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
