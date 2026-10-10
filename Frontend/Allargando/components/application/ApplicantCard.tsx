import React from "react";
import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { ApplicationSummary, ApplicationStatus } from "@/types/application";
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

interface ApplicantCardProps {
  application: ApplicationSummary;
  // 수락/거절/철회 API 호출은 부모(지원자 목록 화면)가 담당 — 이 카드는 버튼과 상태만 그린다.
  // PENDING이 아니면 onAccept/onReject를, ACCEPTED가 아니면 onRevoke를 넘기지 않아도 되지만
  // (버튼 자체가 안 그려짐), 부모가 항상 셋 다 넘겨도 무방하도록 옵셔널로 둔다.
  onAccept?: () => void;
  onReject?: () => void;
  onRevoke?: () => void;
  // 신고·차단 같은 부가 동작 진입점(⋯ 버튼). 안 넘기면 버튼이 그려지지 않는다
  onMore?: () => void;
  // 이 지원서에 대한 수락/거절/철회 요청이 진행 중인 동안 true — 버튼을 잠가 중복 탭을 막는다.
  // (accept/reject/revoke는 PATCH지만 멱등이 아니라 재요청 시 400이 나므로 이 잠금이 중요하다)
  processing?: boolean;
}

export default function ApplicantCard({
  application,
  onAccept,
  onReject,
  onRevoke,
  onMore,
  processing = false,
}: ApplicantCardProps) {
  // instrument는 지원자가 이 글에 지원하며 고른 악기(수락하면 이 악기 정원이 찬다).
  // applicant.instrument는 프로필 악기라 다를 수 있어 헤더에는 고른 악기를 보여준다
  const { applicant, status, additionalInfo, instrument } = application;

  return (
    <View style={styles.card}>
      <View style={styles.headerRow}>
        <Text style={styles.nickname} numberOfLines={1}>
          {applicant.nickname} · {instrument}
        </Text>
        <View style={styles.headerRight}>
          <View style={[styles.statusBadge, { borderColor: STATUS_COLOR[status] }]}>
            <Text style={[styles.statusText, { color: STATUS_COLOR[status] }]}>
              {STATUS_LABEL[status]}
            </Text>
          </View>
          {onMore && (
            <TouchableOpacity
              onPress={onMore}
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              accessibilityLabel="지원자 신고·차단 메뉴"
            >
              <Ionicons name="ellipsis-horizontal" size={18} color={colors.textMuted} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      <View style={styles.metaList}>
        <IconText icon="person-outline" text={`이름 ${applicant.name}`} />
        <IconText icon="call-outline" text={applicant.phoneNumber} />
      </View>

      {applicant.careers.length > 0 && (
        <View style={styles.careerList}>
          {applicant.careers.map((c) => (
            <Text key={c.id} style={styles.careerText} numberOfLines={1}>
              · {c.organization} — {c.contexts}
            </Text>
          ))}
        </View>
      )}

      {additionalInfo ? (
        <View style={styles.additionalInfoBox}>
          <Text style={styles.additionalInfoText}>{additionalInfo}</Text>
        </View>
      ) : null}

      {status === "PENDING" && (
        <View style={styles.actionRow}>
          <ThemedButton
            title="거절"
            variant="outline"
            style={styles.actionButton}
            disabled={processing}
            onPress={() => onReject?.()}
          />
          <ThemedButton
            title="수락"
            style={styles.actionButton}
            disabled={processing}
            onPress={() => onAccept?.()}
          />
        </View>
      )}

      {status === "ACCEPTED" && (
        <View style={styles.actionRow}>
          <ThemedButton
            title="수락 철회"
            variant="outline"
            style={styles.actionButton}
            disabled={processing}
            onPress={() => onRevoke?.()}
          />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  headerRight: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    padding: 14,
    gap: 8,
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  nickname: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.textPrimary,
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
  metaList: {
    gap: 4,
  },
  careerList: {
    gap: 2,
  },
  careerText: {
    fontSize: 12,
    color: colors.textMuted,
  },
  additionalInfoBox: {
    backgroundColor: colors.background,
    borderRadius: 8,
    padding: 10,
  },
  additionalInfoText: {
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  actionRow: {
    flexDirection: "row",
    gap: 10,
    marginTop: 4,
  },
  actionButton: {
    flex: 1,
    height: 40,
  },
});
