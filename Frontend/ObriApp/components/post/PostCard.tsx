import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { PostStatus, PostSummary } from "@/types/post";
import { formatEventDateTime, getDday } from "@/utils/datetime";
import IconText from "@/components/common/IconText";

interface PostCardProps {
  post: PostSummary;
  onPress?: (id: number) => void;
  // 모집 상태 뱃지를 보일지. 공개 목록은 모집 중인 글만 나와 필요 없고, 마감한 글도 섞여 있는 내 모집글 목록에서 쓴다
  showStatus?: boolean;
}

const STATUS_LABEL: Record<PostStatus, string> = {
  OPEN: "모집중",
  PARTIALLY_CLOSED: "부분 마감",
  CLOSED: "마감",
};

const STATUS_COLOR: Record<PostStatus, string> = {
  OPEN: colors.primaryLight,
  PARTIALLY_CLOSED: colors.textSecondary,
  CLOSED: colors.textMuted,
};

export default function PostCard({ post, onPress, showStatus = false }: PostCardProps) {
  const dday = getDday(post.eventAt);

  return (
    <TouchableOpacity
      style={styles.card}
      activeOpacity={0.8}
      onPress={() => onPress?.(post.id)}
    >
      {/* 좌측 썸네일 (추후 이미지로 교체할 자리) */}
      <View style={styles.thumbnail}>
        <Ionicons name="musical-note" size={26} color={colors.primaryLight} />
      </View>

      {/* 우측 정보 */}
      <View style={styles.info}>
        <View style={styles.titleRow}>
          {showStatus && (
            <View style={[styles.statusBadge, { borderColor: STATUS_COLOR[post.status] }]}>
              <Text style={[styles.statusText, { color: STATUS_COLOR[post.status] }]}>
                {STATUS_LABEL[post.status]}
              </Text>
            </View>
          )}
          <Text style={styles.title} numberOfLines={1}>
            {post.title}
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
        <View style={styles.metaList}>
          <IconText icon="calendar-outline" text={formatEventDateTime(post.eventAt)} />
          <IconText icon="location-outline" text={post.location} />
        </View>
        <View style={styles.instrumentRow}>
          {post.instruments.map(({ instrument, confirmed, people }) => (
            <View key={instrument} style={styles.instrumentChip}>
              <Text style={styles.instrumentText}>
                {instrument} {confirmed}/{people}
              </Text>
            </View>
          ))}
        </View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  titleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  statusBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    borderWidth: 1,
    flexShrink: 0,
  },
  statusText: {
    fontSize: 10,
    fontWeight: "700",
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
  },
  info: {
    flex: 1,
    justifyContent: "center",
    gap: 8,
  },
  title: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  metaList: {
    gap: 4,
  },
  instrumentRow: {
    flexDirection: "row",
    alignItems: "center",
    flexWrap: "wrap",
    gap: 6,
  },
  instrumentChip: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    backgroundColor: colors.background,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  instrumentText: {
    fontSize: 12,
    color: colors.textSecondary,
  },
});
