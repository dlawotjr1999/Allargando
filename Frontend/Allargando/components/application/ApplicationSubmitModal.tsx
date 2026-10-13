import React, { useState } from "react";
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Modal, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { PostInstrument } from "@/types/post";
import Chip from "@/components/common/Chip";
import ThemedButton from "@/components/common/ThemedButton";

// 백엔드 AppRequestDTO.additionalInfo의 길이 상한(@Size max=255)
const MAX_INFO_LENGTH = 255;

interface ApplicationSubmitModalProps {
  visible: boolean;
  postTitle: string;
  // 그 글의 모집 악기. 정원이 찬 악기(closed)는 고를 수 없게 흐리게 보여준다
  instruments: PostInstrument[];
  // 처음 선택해 둘 악기(내 프로필 악기). 모집 악기에 없거나 정원이 찼으면 선택 없이 시작한다
  defaultInstrument?: string;
  // 제출 API(POST /api/applications/submit) 호출은 부모(post/[id]/index.tsx)가 담당한다 — 이 모달은
  // 입력값만 모아 onSubmit으로 넘기고, 성공/실패에 따른 화면 갱신은 부모 책임.
  submitting: boolean;
  onSubmit: (instrument: string, additionalInfo: string) => void;
  onClose: () => void;
}

// 지원 제출 전 지원할 악기(필수)와 어필 문구(선택)를 입력받는 모달. 모집자가 수락하면 이름과 전화번호가
// 모집자에게 공개되므로 제출 버튼 위에 그 사실을 안내한다(대기 중에는 서버가 가려서 준다).
export default function ApplicationSubmitModal({
  visible,
  postTitle,
  instruments,
  defaultInstrument,
  submitting,
  onSubmit,
  onClose,
}: ApplicationSubmitModalProps) {
  const [instrument, setInstrument] = useState("");
  const [additionalInfo, setAdditionalInfo] = useState("");

  // 프로필 악기가 이 글에서 지원 가능한 악기인지 — 아니면 직접 고르게 안내한다
  const defaultSelectable = instruments.some((it) => it.instrument === defaultInstrument && !it.closed);

  // 모달이 열릴 때마다 입력을 초기화한다 (다른 글에 다시 열었을 때 이전 값이 남아있지 않도록).
  // 프로필 악기가 지원 가능하면 미리 선택해 둔다
  function handleShow() {
    setInstrument(defaultSelectable && defaultInstrument ? defaultInstrument : "");
    setAdditionalInfo("");
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onShow={handleShow} onRequestClose={onClose}>
      <View style={styles.wrapper}>
        <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={onClose} />

        <View style={styles.container}>
          <TouchableOpacity style={styles.closeButton} onPress={onClose} accessibilityLabel="닫기">
            <Ionicons name="close" size={14} color={colors.background} />
          </TouchableOpacity>

          <View style={styles.sheet}>
            <ScrollView
              style={styles.card}
              contentContainerStyle={styles.cardContent}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}
            >
              <Text style={styles.title} numberOfLines={2}>
                지원하기
              </Text>
              <Text style={styles.subtitle} numberOfLines={1}>
                {postTitle}
              </Text>

              <View style={styles.divider} />

              <Text style={styles.label}>지원할 악기</Text>
              <View style={styles.chipRow}>
                {instruments.map((it) => (
                  <Chip
                    key={it.instrument}
                    label={`${it.instrument} ${it.confirmed}/${it.people}`}
                    active={instrument === it.instrument}
                    disabled={it.closed}
                    onPress={() => setInstrument(it.instrument)}
                  />
                ))}
              </View>
              {defaultSelectable && instrument === defaultInstrument && (
                <Text style={styles.hint}>
                  내 프로필 악기({defaultInstrument})로 미리 선택해 뒀어요. 다른 악기로도 지원할 수 있어요.
                </Text>
              )}
              {defaultInstrument && !defaultSelectable && (
                <Text style={styles.hint}>
                  내 프로필 악기({defaultInstrument})로는 지원할 수 없는 글이에요. 지원할 악기를 직접 골라주세요.
                </Text>
              )}

              <Text style={[styles.label, styles.labelSpaced]}>어필 문구 (선택)</Text>
              <TextInput
                style={styles.input}
                placeholder="모집자에게 전달할 소개나 어필 문구를 남겨보세요"
                placeholderTextColor={colors.placeholder}
                value={additionalInfo}
                onChangeText={setAdditionalInfo}
                maxLength={MAX_INFO_LENGTH}
                multiline
                numberOfLines={4}
                textAlignVertical="top"
              />
              <Text style={styles.counter}>
                {additionalInfo.length}/{MAX_INFO_LENGTH}
              </Text>

              <View style={styles.notice}>
                <Ionicons name="information-circle-outline" size={16} color={colors.textMuted} />
                <Text style={styles.noticeText}>모집자가 수락하면 내 이름과 전화번호가 모집자에게 공개됩니다.</Text>
              </View>

              <ThemedButton
                title={submitting ? "제출 중..." : "제출하기"}
                onPress={() => onSubmit(instrument, additionalInfo)}
                disabled={submitting || !instrument}
                style={styles.submitButton}
              />
            </ScrollView>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "rgba(0,0,0,0.4)",
    padding: 28,
  },
  container: {
    width: "100%",
    maxHeight: "90%",
  },
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
  sheet: {
    backgroundColor: colors.background,
    borderRadius: 20,
    padding: 20,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  cardContent: {
    padding: 16,
  },
  title: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.primary,
  },
  subtitle: {
    fontSize: 13,
    color: colors.textSecondary,
    marginTop: 4,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: 14,
  },
  label: {
    fontSize: 12,
    color: colors.textMuted,
    letterSpacing: 1,
    marginBottom: 8,
  },
  labelSpaced: {
    marginTop: 16,
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  hint: {
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 8,
    lineHeight: 18,
  },
  input: {
    backgroundColor: colors.background,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    height: 100,
    fontSize: 14,
    color: colors.textPrimary,
  },
  counter: {
    alignSelf: "flex-end",
    fontSize: 11,
    color: colors.textMuted,
    marginTop: 4,
  },
  notice: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginTop: 12,
  },
  noticeText: {
    flex: 1,
    fontSize: 12,
    color: colors.textMuted,
  },
  submitButton: {
    marginTop: 16,
  },
});
