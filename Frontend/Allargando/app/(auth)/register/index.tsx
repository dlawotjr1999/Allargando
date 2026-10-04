import React, { useState } from "react";
import {
  View,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Alert,
} from "react-native";
import { useRouter } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { useRegisterForm } from "@/contexts/RegisterContext";
import ScreenHeader from "@/components/common/ScreenHeader";
import StepIndicator from "@/components/common/StepIndicator";
import ThemedInput from "@/components/common/ThemedInput";
import ThemedButton from "@/components/common/ThemedButton";
import AgreementRow from "@/components/auth/AgreementRow";
import { openLegalDocument, PRIVACY_URL, TERMS_URL } from "@/lib/legal";
import { validateAccountStep } from "@/utils/registerValidation";

export default function RegisterStep1() {
  const router = useRouter();
  const { form, updateForm } = useRegisterForm();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const allAgreed = form.agreeTerms && form.agreePrivacy && form.agreeAge;

  // 다음 단계로. 이메일 형식·비밀번호 길이/일치·필수 약관 동의를 확인한다
  const handleNext = () => {
    const invalid = validateAccountStep(form);
    if (invalid) {
      Alert.alert("입력을 확인해 주세요", invalid);
      return;
    }
    updateForm({ email: form.email.trim() });
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
          title="계정 만들기"
          subtitle="이메일과 비밀번호를 입력해주세요"
        />
        <StepIndicator total={3} current={1} />

        <ThemedInput
          label="이메일"
          icon="mail-outline"
          placeholder="example@email.com"
          value={form.email}
          onChangeText={(v) => updateForm({ email: v })}
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
        />

        <ThemedInput
          label="비밀번호"
          icon="lock-closed-outline"
          placeholder="6자 이상 입력"
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

        <View style={styles.agreements}>
          <AgreementRow
            label="전체 동의"
            checked={allAgreed}
            required={false}
            onToggle={() =>
              updateForm({ agreeTerms: !allAgreed, agreePrivacy: !allAgreed, agreeAge: !allAgreed })
            }
          />
          <View style={styles.agreementDivider} />
          <AgreementRow
            label="이용약관 동의"
            checked={form.agreeTerms}
            onToggle={() => updateForm({ agreeTerms: !form.agreeTerms })}
            onView={() => openLegalDocument("이용약관", TERMS_URL)}
          />
          <AgreementRow
            label="개인정보 수집·이용 동의"
            checked={form.agreePrivacy}
            onToggle={() => updateForm({ agreePrivacy: !form.agreePrivacy })}
            onView={() => openLegalDocument("개인정보처리방침", PRIVACY_URL)}
          />
          <AgreementRow
            label="만 14세 이상입니다"
            checked={form.agreeAge}
            onToggle={() => updateForm({ agreeAge: !form.agreeAge })}
          />
        </View>

        <View style={styles.bottom}>
          <ThemedButton title="다음" onPress={handleNext} />
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
    // flexGrow: 1,
    paddingHorizontal: 28,
    paddingTop: 16,
  },
  agreements: {
    marginTop: 8,
  },
  agreementDivider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: 4,
  },
  bottom: {
    marginTop: 24,
    marginBottom: 32,
  },
});
