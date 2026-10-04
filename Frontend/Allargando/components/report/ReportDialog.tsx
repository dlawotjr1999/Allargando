import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  TextInput,
  Modal,
  TouchableOpacity,
  Pressable,
  ScrollView,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { REPORT_REASONS } from "@/constants/reportReasons";
import { submitReport } from "@/api/report";
import { ApiError } from "@/lib/apiClient";
import { ReportReason, ReportTarget } from "@/types/safety";
import ThemedButton from "@/components/common/ThemedButton";

interface ReportDialogProps {
  // 신고할 대상. null이면 닫힌 상태 — 화면은 "무엇을 신고 중인가"만 state로 들고 있으면 된다
  target: ReportTarget | null;
  onClose: () => void;
}

// 신고 다이얼로그 — 사유 선택 + 선택 설명 입력 → 신고 접수. 모집글·유저 신고가 같은 UI를 쓴다.
// 제출은 응답이 올 때까지 버튼을 잠가 연속 탭을 막고, 실패하면 열어둔 채로 에러만 알려 재시도할 수 있게 한다.
export default function ReportDialog({ target, onClose }: ReportDialogProps) {
  const [reason, setReason] = useState<ReportReason | null>(null);
  const [detail, setDetail] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // 대상이 바뀌어 새로 열릴 때마다 입력을 비운다(이전 신고의 사유·설명이 남아 있지 않게)
  useEffect(() => {
    if (target) {
      setReason(null);
      setDetail("");
    }
  }, [target]);

  const handleSubmit = async () => {
    if (!target || !reason || submitting) return;
    setSubmitting(true);
    try {
      await submitReport(target, { reason, detail: detail.trim() || undefined });
      onClose();
      Alert.alert("신고가 접수되었어요", "확인 후 필요한 조치를 취할게요. 알려주셔서 감사합니다.");
    } catch (err) {
      Alert.alert("신고 실패", err instanceof ApiError ? err.message : "잠시 후 다시 시도해주세요.");
    } finally {
      setSubmitting(false);
    }
  };

  const title = target?.type === "USER" ? "사용자 신고" : "모집글 신고";

  return (
    <Modal visible={target !== null} transparent animationType="fade" onRequestClose={onClose}>
      <KeyboardAvoidingView
        style={styles.overlay}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="신고 닫기" />
        <View style={styles.sheet}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>신고 사유를 선택해주세요.</Text>

          <ScrollView keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
            {REPORT_REASONS.map((item) => {
              const selected = reason === item.value;
              return (
                <TouchableOpacity
                  key={item.value}
                  style={styles.reasonRow}
                  onPress={() => setReason(item.value)}
                  activeOpacity={0.7}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                >
                  <Ionicons
                    name={selected ? "radio-button-on" : "radio-button-off"}
                    size={20}
                    color={selected ? colors.primary : colors.placeholder}
                  />
                  <Text style={styles.reasonText}>{item.label}</Text>
                </TouchableOpacity>
              );
            })}

            <TextInput
              style={styles.detailInput}
              placeholder="자세한 내용을 적어주세요 (선택)"
              placeholderTextColor={colors.placeholder}
              value={detail}
              onChangeText={setDetail}
              multiline
              maxLength={500}
              textAlignVertical="top"
            />
          </ScrollView>

          <View style={styles.buttonRow}>
            <ThemedButton
              title="취소"
              variant="outline"
              onPress={onClose}
              style={styles.button}
            />
            <ThemedButton
              title="신고하기"
              onPress={handleSubmit}
              loading={submitting}
              disabled={!reason}
              style={styles.button}
            />
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: "center",
    paddingHorizontal: 24,
    backgroundColor: "rgba(0,0,0,0.4)",
  },
  sheet: {
    maxHeight: "85%",
    backgroundColor: colors.background,
    borderRadius: 16,
    padding: 20,
  },
  title: {
    fontSize: 17,
    fontWeight: "700",
    color: colors.textPrimary,
  },
  subtitle: {
    fontSize: 13,
    color: colors.textMuted,
    marginTop: 4,
    marginBottom: 12,
  },
  reasonRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingVertical: 11,
  },
  reasonText: {
    fontSize: 14,
    color: colors.textPrimary,
  },
  detailInput: {
    marginTop: 8,
    height: 90,
    backgroundColor: colors.surface,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingTop: 12,
    fontSize: 14,
    color: colors.textPrimary,
  },
  buttonRow: {
    flexDirection: "row",
    gap: 10,
    marginTop: 16,
  },
  button: {
    flex: 1,
  },
});
