import React, { useState } from "react";
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { useAuth } from "@/contexts/AuthContext";
import { useRegisterForm } from "@/contexts/RegisterContext";
import ScreenHeader from "@/components/common/ScreenHeader";
import StepIndicator from "@/components/common/StepIndicator";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";
import AgreementSection from "@/components/auth/AgreementSection";
import {
  getLoginIdError,
  LOGIN_ID_HINT,
  normalizeLoginId,
  validateAccountStep,
} from "@/utils/registerValidation";

// 가입 1단계(계정). 전화 인증만 하고 이메일 연결 전에 끊긴 계정(user가 있음)이 이어서 가입하는 경우에도 이 화면으로
// 오며, 이때는 뒤로 갈 곳이 없으므로 뒤로 가기를 숨기고 다른 계정으로 로그인할 수 있게 한다.
export default function RegisterStep1() {
  const router = useRouter();
  const { user, signOut } = useAuth();
  const resuming = !!user;
  const { form, updateForm } = useRegisterForm();
  // 입력 중에도 형식이 틀리면 안내문을 붉게 바꿔 바로 알려준다(빈 값은 안내문만)
  const loginIdError = getLoginIdError(form.loginId);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);


  // 다음 단계로. 이메일 형식·비밀번호 길이/일치·필수 약관 동의를 확인한다
  const handleNext = () => {
    const invalid = validateAccountStep(form);
    if (invalid) {
      Alert.alert("입력을 확인해 주세요", invalid);
      return;
    }
    updateForm({ loginId: normalizeLoginId(form.loginId) });
    router.push("/(auth)/register/profile");
  };

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <ScreenHeader
          showBack={!resuming}
          title="계정 만들기"
          subtitle={
            resuming
              ? "전화번호 인증은 끝났어요. 로그인에 쓸 아이디와 비밀번호를 정해주세요"
              : "아이디와 비밀번호를 입력해주세요"
          }
        />
        <StepIndicator total={3} current={1} />

        <ThemedInput
          label="아이디"
          icon="person-outline"
          placeholder="영문 소문자·숫자·_ 4~20자"
          value={form.loginId}
          onChangeText={(v) => updateForm({ loginId: v })}
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={20}
        />
        <Text style={[styles.idHint, loginIdError ? styles.idHintError : null]}>
          {LOGIN_ID_HINT}
        </Text>

        <ThemedInput
          label="비밀번호"
          icon="lock-closed-outline"
          placeholder="8자 이상 입력"
          value={form.password}
          onChangeText={(v) => updateForm({ password: v })}
          secureTextEntry={!showPassword}
          rightElement={
            <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
              <Ionicons
                name={showPassword ? "eye-off-outline" : "eye-outline"}
                size={18}
                color={colors.placeholder}
              />
            </TouchableOpacity>
          }
        />

        <ThemedInput
          label="비밀번호 확인"
          icon="lock-closed-outline"
          placeholder="비밀번호를 다시 입력"
          value={form.passwordConfirm}
          onChangeText={(v) => updateForm({ passwordConfirm: v })}
          secureTextEntry={!showConfirm}
          rightElement={
            <TouchableOpacity onPress={() => setShowConfirm(!showConfirm)}>
              <Ionicons
                name={showConfirm ? "eye-off-outline" : "eye-outline"}
                size={18}
                color={colors.placeholder}
              />
            </TouchableOpacity>
          }
        />

        <AgreementSection form={form} onChange={updateForm} />

        <View style={styles.bottom}>
          <ThemedButton title="다음" onPress={handleNext} />
          {resuming && (
            <TouchableOpacity style={styles.switchAccount} onPress={() => signOut().catch(() => {})}>
              <Text style={styles.switchAccountText}>다른 계정으로 로그인</Text>
            </TouchableOpacity>
          )}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  // 입력줄(아래 여백 16)에 붙여 안내문을 입력칸 바로 밑에 둔다
  idHint: {
    fontSize: 11,
    color: colors.textMuted,
    lineHeight: 16,
    marginTop: -10,
    marginBottom: 16,
  },
  idHintError: {
    color: colors.danger,
  },
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scrollContent: {
    // flexGrow: 1,
    paddingHorizontal: 28,
    paddingTop: 16,
  },
  bottom: {
    marginTop: 24,
    marginBottom: 32,
  },
  switchAccount: {
    alignItems: "center",
    paddingVertical: 14,
  },
  switchAccountText: {
    fontSize: 13,
    color: colors.textMuted,
    textDecorationLine: "underline",
  },
});
