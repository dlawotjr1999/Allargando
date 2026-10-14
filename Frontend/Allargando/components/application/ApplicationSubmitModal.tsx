import React, { useState } from "react";
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Modal, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { PostInstrument } from "@/types/post";
import Chip from "@/components/common/Chip";
import ThemedButton from "@/components/common/ThemedButton";
import AgreementRow from "@/components/auth/AgreementRow";
import { openLegalDocument, PRIVACY_URL } from "@/lib/legal";

// 백엔드 AppRequestDTO.additionalInfo의 길이 상한(@Size max=255)
const MAX_INFO_LENGTH = 255;

// 제3자 제공 동의 항목표(docs/T&C/apply-consent-screen.md와 같은 내용을 유지한다)
const CONSENT_ROWS: [string, string][] = [
  ["제공받는 자", "현재 지원하려는 모집글의 작성자(모집자)"],
  ["제공 목적", "합주·공연 참여 연락 및 일정 조율"],
  ["제공 항목", "이름, 휴대전화번호"],
  ["제공 시점", "지원자가 동의한 뒤 모집자가 지원을 수락한 때"],
  ["보유·이용 기간", "해당 모집의 참여 연락 및 일정 조율 목적을 달성할 때까지. 목적 외 이용 및 불필요한 보관은 허용되지 않음"],
  ["동의 거부 시", "거부할 수 있으나 해당 모집에는 지원할 수 없음. 다른 서비스 기능은 이용 가능"],
];

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
  // agreedToThirdParty는 제3자 제공 동의 체크 여부 — 체크하지 않으면 제출 버튼이 눌리지 않아 항상 true로 온다
  onSubmit: (instrument: string, additionalInfo: string, agreedToThirdParty: boolean) => void;
  onClose: () => void;
}

// 지원 제출 전 지원할 악기(필수)와 어필 문구(선택)를 입력받는 모달. 모집자가 수락하면 이름과 전화번호가
// 모집자에게 공개되므로(대기 중에는 서버가 가려서 준다) 개인정보 제3자 제공 동의를 받는다 —
// 문안은 docs/T&C/apply-consent-screen.md이고, 체크박스는 기본 미선택이며 체크해야 "동의하고 지원하기"가 눌린다.
// 문안을 바꾸면 constants/consent.ts의 버전을 올릴 것.
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
  const [agreed, setAgreed] = useState(false);

  // 프로필 악기가 이 글에서 지원 가능한 악기인지 — 아니면 직접 고르게 안내한다
  const defaultSelectable = instruments.some((it) => it.instrument === defaultInstrument && !it.closed);

  // 모달이 열릴 때마다 입력을 초기화한다 (다른 글에 다시 열었을 때 이전 값이 남아있지 않도록).
  // 프로필 악기가 지원 가능하면 미리 선택해 둔다
  function handleShow() {
    setInstrument(defaultSelectable && defaultInstrument ? defaultInstrument : "");
    setAdditionalInfo("");
    setAgreed(false); // 동의는 지원할 때마다 새로 받는다 — 이전 글에서 체크했어도 기본 미선택으로 시작
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

              <View style={styles.divider} />

              <Text style={styles.consentTitle}>개인정보 제3자 제공 동의</Text>
              <Text style={styles.consentIntro}>
                해당 모집글에 지원하고 수락되면, 합주·공연 참여 조율을 위해 아래와 같이 개인정보가 모집자에게 공개됩니다.
              </Text>
              <View style={styles.consentTable}>
                {CONSENT_ROWS.map(([label, value]) => (
                  <View key={label} style={styles.consentRow}>
                    <Text style={styles.consentLabel}>{label}</Text>
                    <Text style={styles.consentValue}>{value}</Text>
                  </View>
                ))}
              </View>
              <AgreementRow
                label="위 개인정보 제3자 제공에 동의합니다."
                checked={agreed}
                onToggle={() => setAgreed((value) => !value)}
                onView={() => openLegalDocument("개인정보처리방침", PRIVACY_URL)}
              />
              <Text style={styles.consentNote}>
                연락처는 모집자가 지원을 수락한 동안에만 앱에서 조회할 수 있습니다. 다만 모집자가 이미 외부에 별도로
                저장한 정보까지 서비스가 원격으로 삭제할 수는 없습니다.
              </Text>

              <ThemedButton
                title={submitting ? "제출 중..." : "동의하고 지원하기"}
                onPress={() => onSubmit(instrument, additionalInfo, agreed)}
                disabled={submitting || !instrument || !agreed}
                style={styles.submitButton}
              />
              <ThemedButton title="취소" variant="outline" onPress={onClose} disabled={submitting} style={styles.cancelButton} />
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
  consentTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: colors.textPrimary,
  },
  consentIntro: {
    fontSize: 12,
    color: colors.textSecondary,
    lineHeight: 18,
    marginTop: 6,
  },
  consentTable: {
    marginTop: 10,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 4,
  },
  consentRow: {
    flexDirection: "row",
    gap: 10,
    paddingVertical: 6,
  },
  consentLabel: {
    width: 84,
    fontSize: 12,
    color: colors.textMuted,
  },
  consentValue: {
    flex: 1,
    fontSize: 12,
    color: colors.textPrimary,
    lineHeight: 17,
  },
  consentNote: {
    fontSize: 11,
    color: colors.textMuted,
    lineHeight: 16,
    marginTop: 4,
  },
  submitButton: {
    marginTop: 16,
  },
  cancelButton: {
    marginTop: 8,
  },
});
