import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, Modal, Linking, Alert } from "react-native";
import { Image } from "expo-image";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { Concert } from "@/types/concert";
import { formatDate } from "@/utils/datetime";
import Tag from "@/components/common/Tag";
import IconText from "@/components/common/IconText";
import ThemedButton from "@/components/common/ThemedButton";

interface ConcertDetailModalProps {
  concert: Concert | null;
  onClose: () => void;
}

export default function ConcertDetailModal({ concert, onClose }: ConcertDetailModalProps) {
  // 콩쿠르의 "지원하기"와 달리, 연주회는 신청 대상이 아니라 KOPIS 상세 페이지로 안내만 한다
  const handleViewSource = async () => {
    if (!concert) return;
    try {
      await Linking.openURL(concert.sourceUrl);
    } catch {
      Alert.alert("링크를 열 수 없어요", "잠시 후 다시 시도해주세요.");
    }
  };

  return (
    <Modal visible={!!concert} transparent animationType="fade" onRequestClose={onClose}>
      {/* 전체 래퍼: 반투명 배경 + 중앙 정렬 */}
      <View style={styles.wrapper}>
        {/* 바깥 터치 시 닫기 */}
        <TouchableOpacity
          style={StyleSheet.absoluteFill}
          activeOpacity={1}
          onPress={onClose}
        />

        {/* X 버튼 절대 위치를 위한 relative 컨테이너 */}
        <View style={styles.container}>
          {/* 원형 X 버튼 — 우측 상단 모서리에 걸침 */}
          <TouchableOpacity style={styles.closeButton} onPress={onClose}>
            <Ionicons name="close" size={14} color={colors.background} />
          </TouchableOpacity>

          {/* 크림 배경 박스 */}
          <View style={styles.sheet}>
            {/* 흰색 카드 프레임 */}
            <View style={styles.card}>
              {concert?.posterUrl && (
                // 목록 카드와 같은 주소·캐시를 써서, 카드에서 이미 받은 포스터는 다시 받지 않고 바로 보인다
                <Image
                  source={concert.posterUrl}
                  style={styles.poster}
                  contentFit="contain"
                  cachePolicy="memory-disk"
                  transition={150}
                />
              )}

              <Text style={styles.title} numberOfLines={2}>
                {concert?.title}
              </Text>

              <View style={styles.badgeRow}>
                {concert && <Tag label={concert.category} variant="accent" />}
              </View>

              <View style={styles.divider} />

              <View style={styles.metaList}>
                <IconText
                  icon="location-outline"
                  text={concert ? `${concert.venue} · ${concert.region}` : ""}
                />
                <IconText
                  icon="calendar-outline"
                  text={
                    concert
                      ? `${formatDate(concert.startDate)} ~ ${formatDate(concert.endDate)}`
                      : ""
                  }
                />
              </View>

              <View style={styles.divider} />

              <ThemedButton title="공연 정보 보기" onPress={handleViewSource} />
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  // 반투명 배경 + 중앙 정렬. padding이 크림 박스의 상하좌우 여백을 결정
  wrapper: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.4)",
    padding: 28,
  },
  // X 버튼을 절대 위치로 걸치기 위한 relative 컨테이너
  container: {
    width: "100%",
  },
  // 원형 X 버튼 — 크림 박스 우측 상단 모서리에 걸침
  closeButton: {
    position: "absolute",
    top: -8,
    right: -8,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.primary,
    justifyContent: "center",
    alignItems: "center",
    zIndex: 1,
  },
  // 크림 배경 박스 — 사방 동일 패딩
  sheet: {
    backgroundColor: colors.background,
    borderRadius: 20,
    padding: 20,
  },
  // 흰색 카드 프레임
  card: {
    backgroundColor: colors.surface,
    borderRadius: 14,
    padding: 16,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    gap: 14,
  },
  // 포스터는 대부분 세로형이라 잘라 채우면(cover) 제목·날짜가 잘린다. 높이를 고정하고 전체가 보이게(contain) 줄여
  // 비율이 제각각이어도 상자 크기는 같게 두고, 남는 좌우는 배경색으로 채운다.
  // 모달에 스크롤이 없어 작은 화면(높이 약 640)에서도 버튼까지 들어오도록 높이를 280으로 둔다
  poster: {
    width: "100%",
    height: 280,
    borderRadius: 10,
    backgroundColor: colors.background,
  },
  title: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.primary,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 6,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
  },
  metaList: {
    gap: 8,
  },
});
