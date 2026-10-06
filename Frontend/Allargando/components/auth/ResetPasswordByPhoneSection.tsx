import React, { useEffect, useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { useAuth, type PhoneConfirmation } from "@/contexts/AuthContext";
import { describeAuthError } from "@/lib/authErrors";
import { PASSWORD_MIN, validateNewPassword } from "@/utils/registerValidation";
import PhoneCodeForm from "@/components/auth/PhoneCodeForm";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";

// 비밀번호 찾기(전화번호). 가입 때 인증한 전화번호로 인증번호를 확인하면 그 계정으로 잠시 로그인되고, 곧바로 새
// 비밀번호를 정해 바꾼다. 이메일을 몰라도 쓸 수 있고, 방금 전화로 인증했으므로 재로그인을 요구받지 않는다.
// 비밀번호를 바꾸면 로그아웃하고 로그인 화면으로 보낸다(새 비밀번호로 직접 로그인하게 한다). 도중에 이 영역을 벗어나면
// 전화번호로 들어간 임시 로그인을 로그아웃해, 비밀번호를 정하지 않은 채 로그인된 상태가 남지 않게 한다.
export default function ResetPasswordByPhoneSection() {
  const router = useRouter();
  const { sendRecoveryCode, confirmRecoveryCode, changePasswordAfterRecovery, endRecovery } = useAuth();
  const [phoneNumber, setPhoneNumber] = useState("");
  // 전화 인증을 통과했는지. true면 번호 입력 대신 새 비밀번호 입력을 보여 준다
  const [verified, setVerified] = useState(false);
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [saving, setSaving] = useState(false);

  // 이 영역을 벗어날 때(탭 전환·화면 이동) 인증으로 들어온 임시 로그인이 남아 있으면 로그아웃한다
  useEffect(() => {
    return () => {
      endRecovery();
    };
    // endRecovery는 렌더마다 새로 만들어지지만 내부에서 ref만 읽어 처음 렌더의 것을 써도 동작이 같다 — 벗어날 때만 부른다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 인증번호 확인. 이메일은 이 화면에서 필요 없으므로 버리고, 통과 여부만 폼에 알린다(실패는 오류로 던져 폼이 안내한다)
  const confirmCode = async (confirmation: PhoneConfirmation, code: string) => {
    await confirmRecoveryCode(confirmation, code);
  };

  // 새 비밀번호 저장. 입력을 검증한 뒤 바꾸고, 성공하면 로그인 화면으로 보낸다
  const handleSave = async () => {
    const invalid = validateNewPassword(password, passwordConfirm);
    if (invalid) {
      Alert.alert("입력을 확인해 주세요", invalid);
      return;
    }
    setSaving(true);
    try {
      await changePasswordAfterRecovery(password);
      Alert.alert("비밀번호를 바꿨어요", "새 비밀번호로 로그인해 주세요.");
      router.replace("/(auth)/login");
    } catch (err) {
      Alert.alert("비밀번호를 바꾸지 못했어요", describeAuthError(err, "reset"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <View>
      <Text style={styles.description}>
        가입할 때 인증한 전화번호로 본인 확인을 한 뒤, 새 비밀번호를 바로 정할 수 있어요.
      </Text>

      <PhoneCodeForm
        phoneNumber={phoneNumber}
        onChangePhone={setPhoneNumber}
        sendCode={sendRecoveryCode}
        confirmCode={confirmCode}
        onVerified={() => setVerified(true)}
        verified={verified}
        hint="문자로 받은 인증번호로 본인 확인을 해요."
      />

      {verified && (
        <View style={styles.passwordArea}>
          <ThemedInput
            label="새 비밀번호"
            icon="lock-closed-outline"
            placeholder={`${PASSWORD_MIN}자 이상 입력`}
            value={password}
            onChangeText={setPassword}
            secureTextEntry={!showPassword}
            autoCapitalize="none"
            rightElement={
              <TouchableOpacity
                onPress={() => setShowPassword(!showPassword)}
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              >
                <Ionicons
                  name={showPassword ? "eye-off-outline" : "eye-outline"}
                  size={18}
                  color={colors.placeholder}
                />
              </TouchableOpacity>
            }
          />
          <ThemedInput
            label="새 비밀번호 확인"
            icon="lock-closed-outline"
            placeholder="비밀번호를 한 번 더 입력"
            value={passwordConfirm}
            onChangeText={setPasswordConfirm}
            secureTextEntry={!showPassword}
            autoCapitalize="none"
          />
          <ThemedButton title="비밀번호 바꾸기" onPress={handleSave} loading={saving} />
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
  passwordArea: {
    marginTop: 8,
  },
});
