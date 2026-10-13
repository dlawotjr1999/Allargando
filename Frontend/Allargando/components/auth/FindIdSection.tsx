import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import { colors } from "@/constants/theme";
import { useAuth, type PhoneConfirmation } from "@/contexts/AuthContext";
import PhoneCodeForm from "@/components/auth/PhoneCodeForm";
import ThemedButton from "@/components/common/ThemedButton";

interface FindIdSectionProps {
  // 결과 화면에서 "비밀번호 찾기"로 바로 넘어갈 때 부른다(상위 화면이 탭을 바꾼다)
  onGoPasswordTab: () => void;
}

// 아이디 찾기. 가입 때 인증한 전화번호로 인증번호를 받아 확인하면, 그 번호에 연결된 계정의 아이디를 그대로 보여 준다.
// 인증번호는 그 번호의 휴대폰을 가진 사람만 받으므로 본인 확인이 된 뒤에는 가리지 않는다(가리면 정작 잊은 사람이 알아볼 수 없다).
// 인증 과정에서 Firebase가 그 번호의 계정으로 잠시 로그인하므로, 아이디를 읽은 즉시 로그아웃하고 화면을 벗어날 때도
// 한 번 더 정리한다(로그인된 채로 남지 않게).
export default function FindIdSection({ onGoPasswordTab }: FindIdSectionProps) {
  const router = useRouter();
  const { sendRecoveryCode, confirmRecoveryCode, endRecovery } = useAuth();
  const [phoneNumber, setPhoneNumber] = useState("");
  // 인증에 성공해 찾은 결과. null이면 아직 인증 전이고, loginId가 null이면 계정은 있으나 이 앱의 아이디 형식이 아닌 경우
  const [result, setResult] = useState<{ loginId: string | null } | null>(null);

  // 이 영역을 벗어날 때(탭 전환·화면 이동) 인증으로 들어온 임시 로그인이 남아 있으면 로그아웃한다
  useEffect(() => {
    return () => {
      endRecovery();
    };
    // endRecovery는 렌더마다 새로 만들어지지만 내부에서 ref만 읽어 처음 렌더의 것을 써도 동작이 같다 — 벗어날 때만 부른다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 인증번호 확인 + 아이디 조회. 계정이 없거나 가입을 마치지 못한 번호면 오류로 던져 폼이 안내 창을 띄우게 한다.
  // 아이디를 읽은 뒤에는 로그인 상태가 더 필요 없으므로 바로 로그아웃한다
  const confirmCode = async (confirmation: PhoneConfirmation, code: string) => {
    const found = await confirmRecoveryCode(confirmation, code);
    setResult(found);
    await endRecovery();
  };

  return (
    <View>
      <Text style={styles.description}>
        가입할 때 인증한 전화번호를 입력하면 가입된 아이디를 알려드려요.
      </Text>

      <PhoneCodeForm
        phoneNumber={phoneNumber}
        onChangePhone={setPhoneNumber}
        sendCode={sendRecoveryCode}
        confirmCode={confirmCode}
        verified={result !== null}
        hint="문자로 받은 인증번호로 본인 확인을 해요."
      />

      {result && (
        <View style={styles.resultCard}>
          <Text style={styles.resultLabel}>가입된 아이디</Text>
          <Text style={styles.resultValue}>
            {result.loginId ?? "아이디를 확인할 수 없는 계정이에요"}
          </Text>
          <View style={styles.resultActions}>
            <ThemedButton title="로그인하러 가기" onPress={() => router.replace("/(auth)/login")} />
            <ThemedButton title="비밀번호 찾기" variant="outline" onPress={onGoPasswordTab} />
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  description: {
    fontSize: 13,
    color: colors.textMuted,
    lineHeight: 19,
    marginBottom: 20,
  },
  resultCard: {
    marginTop: 8,
    padding: 16,
    borderRadius: 12,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    gap: 6,
  },
  resultLabel: {
    fontSize: 12,
    color: colors.textMuted,
  },
  resultValue: {
    fontSize: 16,
    fontWeight: "600",
    color: colors.textPrimary,
  },
  resultActions: {
    marginTop: 14,
    gap: 10,
  },
});
