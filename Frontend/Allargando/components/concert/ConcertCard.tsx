import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Image } from "expo-image";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { Concert } from "@/types/concert";
import { getDday } from "@/utils/datetime";
import Tag from "@/components/common/Tag";
import IconText from "@/components/common/IconText";

interface ConcertCardProps {
  concert: Concert;
  onPress?: () => void;
}

// D-day는 공연 시작일(startDate) 기준 — "공연이 며칠 남았는가". 시작일이 지났어도 종료일(endDate)이 남은 장기 공연은
// D+n으로 보이면 지난 공연으로 오해하므로 "공연중"으로 표시한다(목록은 종료된 공연을 이미 빼고 내려준다)
export default function ConcertCard({ concert, onPress }: ConcertCardProps) {
  const dday = getDday(concert.startDate);
  const running = dday.expired && !getDday(concert.endDate).expired;

  return (
    <TouchableOpacity style={styles.card} activeOpacity={0.8} onPress={onPress}>
      <View style={styles.thumbnail}>
        {/* 포스터(장당 수백 KB)는 expo-image로 디스크에 캐시해 스크롤·재방문 때 다시 받지 않는다 */}
        {concert.posterUrl ? (
          <Image
            source={concert.posterUrl}
            style={styles.posterImage}
            contentFit="cover"
            cachePolicy="memory-disk"
            transition={150}
          />
        ) : (
          <Ionicons name="musical-notes" size={26} color={colors.primaryLight} />
        )}
      </View>

      <View style={styles.info}>
        <View style={styles.titleRow}>
          <Text style={styles.title} numberOfLines={1}>
            {concert.title}
          </Text>
          {running ? (
            <Text style={[styles.dday, styles.ddayRunning]}>공연중</Text>
          ) : dday.label ? (
            <Text
              style={[
                styles.dday,
                dday.urgent && styles.ddayUrgent,
                dday.expired && styles.ddayExpired,
              ]}
            >
              {dday.label}
            </Text>
          ) : null}
        </View>

        <View style={styles.badgeRow}>
          <Tag label={concert.category} variant="accent" />
        </View>

        <IconText icon="location-outline" text={concert.venue} />
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surface,
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    padding: 14,
    gap: 14,
  },
  thumbnail: {
    width: 56,
    height: 56,
    borderRadius: 10,
    backgroundColor: colors.background,
    alignItems: "center",
    justifyContent: "center",
    overflow: "hidden",
  },
  posterImage: {
    width: "100%",
    height: "100%",
  },
  info: {
    flex: 1,
    justifyContent: "center",
    gap: 6,
  },
  titleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  title: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  dday: {
    fontSize: 11,
    fontWeight: "600",
    color: colors.textMuted,
    flexShrink: 0,
  },
  ddayUrgent: {
    color: colors.danger,
  },
  ddayExpired: {
    color: colors.placeholder,
  },
  ddayRunning: {
    color: colors.primary,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 6,
  },
});
