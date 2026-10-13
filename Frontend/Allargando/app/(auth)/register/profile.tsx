import React from "react";
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Alert } from "react-native";
import { useRouter } from "expo-router";
import { colors } from "@/constants/theme";
import { INSTRUMENTS } from "@/constants/filterOptions";
import { useAuth } from "@/contexts/AuthContext";
import { useRegisterForm } from "@/contexts/RegisterContext";
import { ApiError } from "@/lib/apiClient";
import ScreenHeader from "@/components/common/ScreenHeader";
import StepIndicator from "@/components/common/StepIndicator";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";
import ChipSelect from "@/components/common/ChipSelect";
import PhoneVerification from "@/components/auth/PhoneVerification";
import AgreementSection from "@/components/auth/AgreementSection";
import { isNicknameDuplicated } from "@/api/user";
import {
  formatPhoneNumber,
  getNicknameError,
  NICKNAME_HINT,
  normalizeNickname,
  validateProfileStep,
  getNameError,
  NAME_HINT,
  normalizeName,
  validateConsent,
} from "@/utils/registerValidation";

// 가입 2단계(프로필). 가입을 못 끝낸 채 로그인된 계정(user가 있음)은 1단계(계정)를 건너뛰고 이 화면으로 와서
// 이어서 가입한다 — 이때는 이미 Firebase 계정이 있으므로 뒤로 갈 곳이 없고, 다른 계정으로 로그인할 수 있게 한다.
export default function RegisterStep2() {
  const router = useRouter();
  const { user, signOut } = useAuth();
  const { form, updateForm } = useRegisterForm();
  const resuming = !!user;

  // 입력 중에도 형식이 틀리면 바로 알려준다(빈 값은 안내문만)
  const nameError = getNameError(form.name);
  const nicknameError = getNicknameError(form.nickname);
  // 1단계(약관 동의 화면)를 거치지 않고 이어서 가입하는 경우엔 동의가 비어 있으므로 여기서 받는다
  const needsConsent = resuming && !(form.agreeTerms && form.agreePrivacy && form.agreeAge);

  // 닉네임 중복 확인 (GET /api/users/check/{nickname}, 인증 불필요). 형식·예약어 위반은 서버가 400으로 알려준다
  const handleCheckNickname = async () => {
    const nickname = normalizeNickname(form.nickname);
    if (!nickname) {
      Alert.alert("닉네임 확인", "닉네임을 입력해주세요.");
      return;
    }
    if (nicknameError) {
      Alert.alert("닉네임 확인", nicknameError);
      return;
    }
    try {
      const duplicated = await isNicknameDuplicated(nickname);
      Alert.alert("닉네임 확인", duplicated ? "이미 사용 중인 닉네임입니다." : "사용 가능한 닉네임입니다.");
    } catch (err) {
      Alert.alert(
        "닉네임 확인",
        err instanceof ApiError && err.status === 400 ? err.message : "확인에 실패했어요. 잠시 후 다시 시도해주세요."
      );
    }
  };

  // 다음 단계로. 전화번호는 문자 인증을 마쳐야 넘어갈 수 있다. 닉네임·전화번호는 서버가 입력 문자열 그대로
  // 중복 비교를 하므로 정규화한 값으로 맞춰 둔다
  const handleNext = () => {
    const invalid = validateProfileStep(form);
    if (invalid) {
      Alert.alert("입력을 확인해 주세요", invalid);
      return;
    }
    if (!user?.phoneNumber) {
      Alert.alert("전화번호 인증", "전화번호 인증을 완료해 주세요.");
      return;
    }
    const consentInvalid = validateConsent(form);
    if (consentInvalid) {
      Alert.alert("입력을 확인해 주세요", consentInvalid);
      return;
    }
    updateForm({
      name: normalizeName(form.name),
      nickname: normalizeNickname(form.nickname),
      phoneNumber: formatPhoneNumber(form.phoneNumber) ?? form.phoneNumber,
    });
    router.push("/(auth)/register/career");
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
          title="프로필 설정"
          subtitle={
            resuming ? "가입을 마저 완료해주세요. 프로필 정보를 입력하면 시작할 수 있어요" : "나를 소개할 기본 정보를 입력해주세요"
          }
        />
        <StepIndicator total={3} current={2} />

        <ThemedInput
          label="이름"
          icon="person-outline"
          placeholder="실명 입력"
          value={form.name}
          onChangeText={(v) => updateForm({ name: v })}
          autoCorrect={false}
          maxLength={30}
        />
        <Text style={[styles.nicknameHint, nameError ? styles.hintError : null]}>{NAME_HINT}</Text>

        <View style={styles.nicknameRow}>
          <View style={styles.nicknameInput}>
            <ThemedInput
              label="닉네임"
              icon="person-outline"
              placeholder="닉네임 입력"
              value={form.nickname}
              onChangeText={(v) => updateForm({ nickname: v })}
              autoCapitalize="none"
              autoCorrect={false}
              maxLength={20}
            />
          </View>
          <TouchableOpacity style={styles.checkButton} onPress={handleCheckNickname}>
            <Text style={styles.checkButtonText}>중복 확인</Text>
          </TouchableOpacity>
        </View>
        <Text style={[styles.nicknameHint, nicknameError ? styles.hintError : null]}>
          {NICKNAME_HINT}
        </Text>

        <PhoneVerification phoneNumber={form.phoneNumber} onChangePhone={(v) => updateForm({ phoneNumber: v })} />

        <ChipSelect
          label="악기"
          options={INSTRUMENTS}
          selected={form.instrument}
          onSelect={(v) => updateForm({ instrument: v })}
        />

        {needsConsent && <AgreementSection form={form} onChange={updateForm} />}

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
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scrollContent: {
    paddingHorizontal: 28,
    paddingTop: 16,
  },
  nicknameRow: {
    flexDirection: "row",
    alignItems: "flex-end",
    gap: 8,
  },
  nicknameInput: {
    flex: 1,
  },
  checkButton: {
    backgroundColor: colors.surface,
    borderWidth: 0.5,
    borderColor: colors.border,
    borderRadius: 10,
    paddingHorizontal: 14,
    height: 46,
    justifyContent: "center",
    marginBottom: 16,
  },
  checkButtonText: {
    fontSize: 13,
    color: colors.textSecondary,
  },
  // 입력줄(아래 여백 16)에 붙여 안내문을 입력칸 바로 밑에 둔다
  nicknameHint: {
    fontSize: 11,
    color: colors.textMuted,
    lineHeight: 16,
    marginTop: -10,
    marginBottom: 16,
  },
  hintError: {
    color: colors.danger,
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
