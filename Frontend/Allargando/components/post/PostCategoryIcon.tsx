import React from "react";
import { View, StyleSheet } from "react-native";
import { Image } from "expo-image";
import { colors } from "@/constants/theme";

interface PostCategoryIconProps {
  category: string;
  size?: number;
}

// 칸에서 아이콘이 차지하는 비율. 나머지는 칸 안쪽 여백이다
const ICON_RATIO = 0.72;

const OTHER_ICON = require("@/assets/images/perform_type_icon/other.png");
const CATEGORY_ICONS: Record<string, number> = {
  앙상블: require("@/assets/images/perform_type_icon/ensemble.png"),
  오케스트라: require("@/assets/images/perform_type_icon/orchestra.png"),
  마칭: require("@/assets/images/perform_type_icon/marching.png"),
  버스킹: require("@/assets/images/perform_type_icon/busking.png"),
};

// 모집글 카테고리 아이콘(카드 썸네일·상세 화면 공용). 서버는 카테고리를 문자열로 저장해 예전 값(연주회·합주 등)이
// 남아 있을 수 있으므로, 기타를 포함해 목록에 없는 값은 모두 기본 아이콘으로 보여 준다
export default function PostCategoryIcon({ category, size = 56 }: PostCategoryIconProps) {
  const iconSize = size * ICON_RATIO;

  return (
    <View style={[styles.tile, { width: size, height: size }]}>
      <Image
        source={CATEGORY_ICONS[category] ?? OTHER_ICON}
        style={{ width: iconSize, height: iconSize }}
        contentFit="contain"
        accessibilityLabel={category}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  tile: {
    borderRadius: 10,
    backgroundColor: colors.background,
    alignItems: "center",
    justifyContent: "center",
  },
});
