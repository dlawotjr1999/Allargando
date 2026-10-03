import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";
import { ApplicationSummary, ApplicationStatus } from "@/types/application";
import { formatEventDateTime } from "@/utils/datetime";
import IconText from "@/components/common/IconText";

const STATUS_LABEL: Record<ApplicationStatus, string> = {
  PENDING: "검토 중",
  ACCEPTED: "수락됨",
  REJECTED: "거절됨",
  CANCELLED: "취소됨",
  REVOKED: "철회됨",
};

const STATUS_COLOR: Record<ApplicationStatus, string> = {
  PENDING: colors.primaryLight,
  ACCEPTED: colors.success,
  REJECTED: colors.danger,
  CANCELLED: colors.textMuted,
  REVOKED: colors.textMuted,
};

interface ApplicationCardProps {
  item: ApplicationSummary;
}

export default function ApplicationCard({ item }: ApplicationCardProps) {
  return (
    <View style={styles.appCard}>
      <View style={styles.appCardHeader}>
        <View style={styles.categoryBadge}>
          <Text style={styles.categoryText}>{item.post.category}</Text>
        </View>
        <View style={[styles.statusBadge, { borderColor: STATUS_COLOR[item.status] }]}>
          <Text style={[styles.statusText, { color: STATUS_COLOR[item.status] }]}>
            {STATUS_LABEL[item.status]}
          </Text>
        </View>
      </View>
      <Text style={styles.appCardTitle} numberOfLines={1}>
        {item.post.title}
      </Text>
      <View style={styles.appCardMeta}>
        <IconText icon="calendar-outline" text={formatEventDateTime(item.post.eventAt)} />
        <IconText icon="location-outline" text={item.post.location} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  appCard: {
    backgroundColor: colors.surface,
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    padding: 14,
    gap: 8,
  },
  appCardHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  categoryBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    backgroundColor: colors.background,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  categoryText: {
    fontSize: 11,
    color: colors.textSecondary,
    fontWeight: "600",
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1,
  },
  statusText: {
    fontSize: 11,
    fontWeight: "700",
  },
  appCardTitle: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  appCardMeta: {
    gap: 3,
  },
});
