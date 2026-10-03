import React from "react";
import { View, Text, Image, StyleSheet, useWindowDimensions } from "react-native";
import { colors } from "@/constants/theme";

const musicBg = require("@/assets/images/music-bg.png");
const logoImg = require("@/assets/images/logo-img.png");

// 번들 에셋의 실제 픽셀 비율을 가져와 투명 여백 없이 정확한 높이 계산
const { width: srcW, height: srcH } = Image.resolveAssetSource(logoImg);
const LOGO_ASPECT = srcW / srcH;

export default function LoadingScreen() {
  const { width: screenWidth } = useWindowDimensions();
  const logoWidth = screenWidth * 0.55;
  const logoHeight = logoWidth / LOGO_ASPECT;

  return (
    <View style={styles.container}>
      <Image source={musicBg} style={styles.backgroundImage} />

      <View style={styles.content}>
        <Image
          source={logoImg}
          style={{ width: logoWidth, height: logoHeight }}
          resizeMode="contain"
        />

        <View style={styles.divider} />

        <Text style={styles.tagline}>음대생을 위한 연주 플랫폼</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  backgroundImage: {
    // RN 0.86의 StyleSheet.absoluteFill은 스프레드할 수 없는 타입이라 속성을 직접 펼쳐 쓴다
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    width: "100%",
    height: "100%",
    resizeMode: "cover",
  },
  content: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  divider: {
    width: 72,
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.primary,
    opacity: 0.3,
    marginTop: -25,
    marginBottom: 14,
  },
  tagline: {
    fontSize: 12,
    color: colors.textSecondary,
    letterSpacing: 4,
  },
});
