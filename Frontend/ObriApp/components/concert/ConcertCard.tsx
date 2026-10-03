import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, Image } from "react-native";
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

// D-day는 콩쿠르 때의 접수마감(deadline) 대신 공연 시작일(startDate) 기준 — "공연이 며칠 남았는가"
export default function ConcertCard({ concert, onPress }: ConcertCardProps) {
  const dday = getDday(concert.startDate);

  return (
    <TouchableOpacity style={styles.card} activeOpacity={0.8} onPress={onPress}>
      <View style={styles.thumbnail}>
        {concert.posterUrl ? (
          <Image source={{ uri: concert.posterUrl }} style={styles.posterImage} resizeMode="cover" />
        ) : (
          <Ionicons name="musical-notes" size={26} color={colors.primaryLight} />
        )}
      </View>

      <View style={styles.info}>
        <View style={styles.titleRow}>
          <Text style={styles.title} numberOfLines={1}>
            {concert.title}
          </Text>
          {dday.label ? (
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
          <Tag label={concert.category} />
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
  badgeRow: {
    flexDirection: "row",
    gap: 6,
  },
});
