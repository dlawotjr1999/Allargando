import React from "react";
import { View, Image, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";

const headerImg = require("@/assets/images/header_image.png");

// 번들 에셋의 실제 픽셀 비율로 로고 너비를 계산해 찌그러짐을 막는다
const { width: srcW, height: srcH } = Image.resolveAssetSource(headerImg);
const LOGO_HEIGHT = 44;
const LOGO_WIDTH = LOGO_HEIGHT * (srcW / srcH);

// 각 탭 상단에 고정되는 한 줄형 로고 헤더
export default function AppHeader() {
  return (
    <View style={styles.header}>
      <Image
        source={headerImg}
        style={{ width: LOGO_WIDTH, height: LOGO_HEIGHT }}
        resizeMode="contain"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    height: 56,
    alignItems: "center",
    justifyContent: "center",
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
    backgroundColor: colors.background,
  },
});
