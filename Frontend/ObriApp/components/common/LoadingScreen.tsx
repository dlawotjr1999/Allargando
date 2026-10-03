import React from "react";
import { View, Text, Image, StyleSheet, useWindowDimensions } from "react-native";
import { colors } from "@/constants/theme";

const backgroundImg = require("@/assets/images/bg_image.png");
const logoImg = require("@/assets/images/logo_image.png");

// 배경 이미지 최상단 하늘색. 화면이 이미지보다 길 때 위쪽 빈 곳을 이 색으로 이어 붙인다
const SKY_COLOR = "#51B4FD";

// 파스텔 톤 조절: 흰색 덮개의 진하기(0~1), 배경 흐림 정도, 로고 불투명도. 값만 바꿔 맞춘다
const PASTEL_OVERLAY = 0.3;
const BG_BLUR = 0.5;
const LOGO_OPACITY = 0.85;

// 번들 에셋의 실제 픽셀 비율을 가져와 높이를 정확히 계산
const bg = Image.resolveAssetSource(backgroundImg);
const BG_ASPECT = bg.width / bg.height;
const logo = Image.resolveAssetSource(logoImg);
const LOGO_ASPECT = logo.width / logo.height;

// 앱 진입(로딩) 화면: 하단 정렬한 벚꽃 앙상블 배경 위 하늘 영역에 로고를 얹는다
export default function LoadingScreen() {
  const { width: screenWidth, height: screenHeight } = useWindowDimensions();
  const logoWidth = screenWidth * 0.7;
  const logoHeight = logoWidth / LOGO_ASPECT;

  return (
    <View style={styles.container}>
      <Image
        source={backgroundImg}
        style={{
          position: "absolute",
          bottom: 0,
          width: screenWidth,
          height: screenWidth / BG_ASPECT,
        }}
        resizeMode="cover"
        blurRadius={BG_BLUR}
      />
      {/* 전체를 흰색으로 덮어 채도를 낮추고 파스텔 톤으로 만든다 */}
      <View
        pointerEvents="none"
        style={[StyleSheet.absoluteFill, { backgroundColor: `rgba(255,255,255,${PASTEL_OVERLAY})` }]}
      />

      <View style={[styles.content, { paddingTop: screenHeight * 0.1 }]}>
        <Image
          source={logoImg}
          style={{ width: logoWidth, height: logoHeight, opacity: LOGO_OPACITY }}
          resizeMode="contain"
        />
        <Text style={styles.tagline}>악기 취미생들을 위한 플랫폼</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: SKY_COLOR,
    overflow: "hidden",
  },
  content: {
    alignItems: "center",
  },
  tagline: {
    fontSize: 12,
    color: colors.primary,
    letterSpacing: 4,
  },
});
