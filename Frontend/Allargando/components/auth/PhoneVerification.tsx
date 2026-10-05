import React, { useEffect, useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { PhoneConfirmation, useAuth } from "@/contexts/AuthContext";
import { describeAuthError } from "@/lib/authErrors";
import { PHONE_CODE_LENGTH, formatPhoneNumber } from "@/utils/registerValidation";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";

// 같은 번호로 인증번호를 다시 받을 수 있게 되기까지 기다리는 시간(초). SMS 연타로 발송 한도를 쓰는 것을 막는다
const RESEND_COOLDOWN_SECONDS = 60;

interface PhoneVerificationProps {
  phoneNumber: string;
  onChangePhone: (phoneNumber: string) => void;
}

// 가입 화면의 전화번호 입력 + SMS 인증번호 확인 영역.
// 번호 입력 → "인증번호 받기" → 6자리 코드 입력 → "확인" 순서로 진행하고, 확인에 성공하면 번호 입력칸이 잠기며
// "인증 완료"로 바뀐다. 인증된 번호는 Firebase 로그인 계정에 기록되므로(user.phoneNumber) 화면을 다시 열어도 유지된다.
export default function PhoneVerification({ phoneNumber, onChangePhone }: PhoneVerificationProps) {
  const { user, sendPhoneCode, confirmPhoneCode } = useAuth();
  const [confirmation, setConfirmation] = useState<PhoneConfirmation | null>(null);
  const [code, setCode] = useState("");
  const [sending, setSending] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  // 확인 직후 로그인 상태(user)가 갱신되기 전까지 "인증 완료"를 바로 보여주기 위한 표시
  const [justVerified, setJustVerified] = useState(false);

  const verified = justVerified || !!user?.phoneNumber;

  // 이미 인증된 계정(가입을 마치지 못하고 돌아온 경우)이면 입력칸에 그 번호를 채워 보여 준다
  useEffect(() => {
    if (user?.phoneNumber && !phoneNumber) {
      onChangePhone(formatPhoneNumber(user.phoneNumber) ?? user.phoneNumber);
    }
  }, [user?.phoneNumber, phoneNumber, onChangePhone]);

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
      setConfirmation(await sendPhoneCode(phoneNumber));
      setCode("");
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      Alert.alert("인증번호를 보내지 못했어요", describeAuthError(err, "phone"));
    } finally {
      setSending(false);
    }
  };

  // 입력한 인증번호 확인. 성공하면 입력칸을 잠그고, 실패하면 이유를 알려 주고 코드를 다시 입력하게 둔다
  const handleConfirm = async () => {
    if (!confirmation) return;
    setConfirming(true);
    try {
      await confirmPhoneCode(confirmation, code);
      setJustVerified(true);
      setConfirmation(null);
      setCode("");
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
        hint="모집글에 지원하면 모집자에게 공개돼요. 문자로 받은 인증번호로 본인 확인을 해요."
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
