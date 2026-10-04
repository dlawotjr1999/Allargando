import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";

interface IconTextProps {
  icon: keyof typeof Ionicons.glyphMap;
  text: string;
}

// 아이콘 + 텍스트 한 줄. 메타 정보(날짜/장소/악기 등) 표시에 재사용.
export default function IconText({ icon, text }: IconTextProps) {
  return (
    <View style={styles.row}>
      <Ionicons name={icon} size={13} color={colors.textMuted} />
      <Text style={styles.text} numberOfLines={1}>
        {text}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  text: {
    flex: 1,
    fontSize: 12,
    color: colors.textSecondary,
  },
});
