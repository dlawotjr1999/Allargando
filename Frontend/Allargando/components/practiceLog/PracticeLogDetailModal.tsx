import React from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Modal,
  ScrollView,
  Dimensions,
  ActivityIndicator,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { PracticeLogDetail } from "@/types/practiceLog";
import { formatDate, formatDuration } from "@/utils/datetime";
import ThemedButton from "@/components/common/ThemedButton";

const { height: SCREEN_HEIGHT } = Dimensions.get("window");

interface PracticeLogDetailModalProps {
  // 모달을 열지 말지는 이 값 하나로 결정 — log가 아직 null이어도(조회 중) visible이면 열려 있어야
  // 스피너를 보여줄 수 있다. 즉 visible과 log의 유무가 서로 독립적이다.
  visible: boolean;
  // 목록(PracticeLogSummary)에는 content가 없어 부모(practice-log.tsx)가 카드 선택 시
  // GET /api/practice-logs/{id}로 별도 조회해 채워 넣는다 — 조회 완료 전까지는 null
  log: PracticeLogDetail | null;
  // 위 상세 조회가 진행 중인 동안 true — 이 모달은 그동안 title/날짜/내용 대신 스피너만 그린다
  loading?: boolean;
  onClose: () => void;
  // 수정·삭제는 API 호출과 목록 state 갱신이 얽혀 있어 부모(practice-log.tsx)가 처리한다.
  // 이 모달은 버튼만 그리고, 조회 중(log null)이거나 삭제 진행 중일 땐 숨긴다.
  onEdit: () => void;
  onDelete: () => void;
  deleting?: boolean;
}

export default function PracticeLogDetailModal({
  visible,
  log,
  loading = false,
  onClose,
  onEdit,
  onDelete,
  deleting = false,
}: PracticeLogDetailModalProps) {
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
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
              {/* 조회 중이거나(loading) 아직 상세가 안 채워졌으면(log null) 본문 대신 스피너만 표시.
                  둘 다 확인하는 이유: log는 부모가 조회 시작 시 null로 리셋해 주는 것을 전제로
                  하지만, 그 전제가 깨지더라도(예: 부모 구현이 바뀌어 이전 상세를 안 지우는 경우)
                  loading이 여전히 안전망 역할을 하도록 남겨둔다. */}
              {loading || !log ? (
                <View style={styles.loadingArea}>
                  <ActivityIndicator color={colors.primary} />
                </View>
              ) : (
                <>
                  <Text style={styles.title} numberOfLines={2}>
                    {log.title}
                  </Text>

                  <View style={styles.metaRow}>
                    <View style={styles.metaItem}>
                      <Ionicons name="calendar-outline" size={14} color={colors.textMuted} />
                      <Text style={styles.metaText}>{formatDate(log.logDate)}</Text>
                    </View>
                    <View style={styles.metaItem}>
                      <Ionicons name="time-outline" size={14} color={colors.textMuted} />
                      <Text style={styles.metaText}>{formatDuration(log.duration)}</Text>
                    </View>
                  </View>

                  <View style={styles.divider} />

                  <ScrollView style={styles.scrollArea} showsVerticalScrollIndicator={false}>
                    <Text style={styles.content}>{log.content}</Text>
                  </ScrollView>

                  <View style={styles.actionRow}>
                    <ThemedButton
                      title="수정"
                      variant="outline"
                      style={styles.actionButton}
                      onPress={onEdit}
                    />
                    <ThemedButton
                      title="삭제"
                      variant="outline"
                      style={styles.actionButton}
                      loading={deleting}
                      onPress={onDelete}
                    />
                  </View>
                </>
              )}
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
  },
  title: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.primary,
    marginBottom: 12,
  },
  metaRow: {
    flexDirection: "row",
    gap: 16,
    marginBottom: 14,
  },
  metaItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  metaText: {
    fontSize: 13,
    color: colors.textMuted,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginBottom: 14,
  },
  // 부모가 auto 높이라 flex로는 크기를 못 잡으므로, 직접 상한을 줘서 이 높이를 넘으면 스크롤되게 함
  scrollArea: {
    maxHeight: SCREEN_HEIGHT * 0.55,
  },
  actionRow: {
    flexDirection: "row",
    gap: 10,
    marginTop: 14,
  },
  actionButton: {
    flex: 1,
    height: 44,
  },
  loadingArea: {
    height: 120,
    alignItems: "center",
    justifyContent: "center",
  },
  content: {
    fontSize: 14,
    color: colors.textSecondary,
    lineHeight: 22,
  },
});
