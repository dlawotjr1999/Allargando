import React, { useEffect, useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import type { PhoneConfirmation } from "@/contexts/AuthContext";
import { describeAuthError } from "@/lib/authErrors";
import { PHONE_CODE_LENGTH, formatPhoneNumber } from "@/utils/registerValidation";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";

// 같은 번호로 인증번호를 다시 받을 수 있게 되기까지 기다리는 시간(초). SMS 연타로 발송 한도를 쓰는 것을 막는다
const RESEND_COOLDOWN_SECONDS = 60;

interface PhoneCodeFormProps {
  phoneNumber: string;
  onChangePhone: (phoneNumber: string) => void;
  // 인증번호를 보내고 확인 핸들을 돌려준다. 가입과 계정 찾기는 보내는 방식이 달라 호출하는 쪽이 정한다
  sendCode: (phoneNumber: string) => Promise<PhoneConfirmation>;
  // 사용자가 입력한 코드를 확인한다. 실패하면 Firebase 오류를 그대로 던진다
  confirmCode: (confirmation: PhoneConfirmation, code: string) => Promise<void>;
  // 코드 확인에 성공한 직후 한 번 불린다
  onVerified?: () => void;
  // true면 번호 입력칸을 잠그고 "인증 완료"로 보여 준다
  verified: boolean;
  hint: string;
}

// 전화번호 입력 + SMS 인증번호 확인 영역(가입·계정 찾기 공용).
// 번호 입력 → "인증번호 받기" → 6자리 코드 입력 → "확인" 순서로 진행한다. 인증 여부(verified)와 보내기·확인 동작은
// 호출하는 쪽이 넘기고, 이 컴포넌트는 입력 상태·재전송 대기·오류 안내만 맡는다
export default function PhoneCodeForm({
  phoneNumber,
  onChangePhone,
  sendCode,
  confirmCode,
  onVerified,
  verified,
  hint,
}: PhoneCodeFormProps) {
  const [confirmation, setConfirmation] = useState<PhoneConfirmation | null>(null);
  const [code, setCode] = useState("");
  const [sending, setSending] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  // 재전송 대기 시간을 1초씩 줄인다
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((seconds) => seconds - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  // 인증번호 발송. 입력한 번호 형식을 먼저 확인하고, 성공하면 코드 입력칸을 연다
  const handleSend = async () => {
    if (!formatPhoneNumber(phoneNumber)) {
      Alert.alert("전화번호 확인", "휴대폰 번호를 010-0000-0000 형식으로 입력해 주세요.");
      return;
    }
    setSending(true);
    try {
      setConfirmation(await sendCode(phoneNumber));
      setCode("");
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      Alert.alert("인증번호를 보내지 못했어요", describeAuthError(err, "phone"));
    } finally {
      setSending(false);
    }
  };

  // 입력한 인증번호 확인. 성공하면 입력칸을 닫고 호출한 쪽에 알리고, 실패하면 이유를 알려 주고 코드를 다시 입력하게 둔다
  const handleConfirm = async () => {
    if (!confirmation) return;
    setConfirming(true);
    try {
      await confirmCode(confirmation, code);
      setConfirmation(null);
      setCode("");
      onVerified?.();
    } catch (err) {
      Alert.alert("인증하지 못했어요", describeAuthError(err, "phone"));
    } finally {
      setConfirming(false);
    }
  };

  return (
    <View>
      <ThemedInput
        label="전화번호"
        icon="call-outline"
        placeholder="010-0000-0000"
        value={phoneNumber}
        onChangeText={onChangePhone}
        keyboardType="phone-pad"
        maxLength={17}
        editable={!verified && !confirmation}
        hint={hint}
      />

      {verified ? (
        <View style={styles.verifiedRow}>
          <Ionicons name="checkmark-circle" size={16} color={colors.success} />
          <Text style={styles.verifiedText}>전화번호 인증이 완료됐어요</Text>
        </View>
      ) : confirmation ? (
        <View style={styles.codeArea}>
          <ThemedInput
            label="인증번호"
            icon="keypad-outline"
            placeholder={`${PHONE_CODE_LENGTH}자리 숫자`}
            value={code}
            onChangeText={(value) => setCode(value.replace(/\D/g, ""))}
            keyboardType="number-pad"
            maxLength={PHONE_CODE_LENGTH}
          />
          <ThemedButton
            title="확인"
            onPress={handleConfirm}
            loading={confirming}
            disabled={code.length !== PHONE_CODE_LENGTH}
          />
          <TouchableOpacity style={styles.resend} onPress={handleSend} disabled={sending || cooldown > 0}>
            <Text style={[styles.resendText, (sending || cooldown > 0) && styles.resendDisabled]}>
              {cooldown > 0 ? `인증번호 다시 받기 (${cooldown}초)` : "인증번호 다시 받기"}
            </Text>
          </TouchableOpacity>
        </View>
      ) : (
        <ThemedButton title="인증번호 받기" variant="outline" onPress={handleSend} loading={sending} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  codeArea: {
    marginTop: 4,
  },
  verifiedRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    marginBottom: 16,
  },
  verifiedText: {
    fontSize: 13,
    color: colors.success,
  },
  resend: {
    alignItems: "center",
    paddingVertical: 12,
  },
  resendText: {
    fontSize: 13,
    color: colors.textSecondary,
    textDecorationLine: "underline",
  },
  resendDisabled: {
    color: colors.textMuted,
    textDecorationLine: "none",
  },
});
