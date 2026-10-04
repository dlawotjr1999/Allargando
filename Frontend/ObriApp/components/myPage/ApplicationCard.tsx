import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";
import { ApplicationSummary, ApplicationStatus } from "@/types/application";
import { formatEventDateTime } from "@/utils/datetime";
import IconText from "@/components/common/IconText";
import ThemedButton from "@/components/common/ThemedButton";

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
  // 지원 취소 동작은 부모(마이페이지)가 담당 — 검토 중(PENDING)인 지원에만 버튼이 그려진다.
  // 넘기지 않으면 버튼 없이 보여주기만 한다
  onCancel?: () => void;
  // 이 지원의 취소 요청이 진행 중인 동안 true — 버튼을 잠가 중복 탭을 막는다
  cancelling?: boolean;
}

// 마이페이지 "내 지원" 카드. 모집글 요약·내 지원 악기·상태를 보여준다
export default function ApplicationCard({ item, onCancel, cancelling = false }: ApplicationCardProps) {
  // 모집이 끝났거나 공연일이 지난 글은 결과를 더 기다릴 필요가 없다는 걸 알려준다
  const postEnded =
    item.post.status === "CLOSED" ? "모집 마감" : new Date(item.post.eventAt) < new Date() ? "공연 종료" : null;

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
        {postEnded && <Text style={styles.postEnded}>{postEnded}</Text>}
      </View>
      <Text style={styles.appCardTitle} numberOfLines={1}>
        {item.post.title}
      </Text>
      <View style={styles.appCardMeta}>
        <IconText icon="calendar-outline" text={formatEventDateTime(item.post.eventAt)} />
        <IconText icon="location-outline" text={item.post.location} />
        <IconText icon="musical-note-outline" text={`지원 악기 ${item.instrument}`} />
      </View>
      {item.status === "PENDING" && onCancel && (
        <ThemedButton
          title={cancelling ? "취소 중..." : "지원 취소"}
          variant="outline"
          disabled={cancelling}
          onPress={onCancel}
          style={styles.cancelButton}
        />
      )}
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
    backgroundColor: colors.accent,
  },
  categoryText: {
    fontSize: 11,
    color: colors.primary,
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
  postEnded: {
    fontSize: 11,
    color: colors.textMuted,
  },
  appCardTitle: {
    fontSize: 15,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  appCardMeta: {
    gap: 3,
  },
  cancelButton: {
    marginTop: 4,
    height: 38,
  },
});
