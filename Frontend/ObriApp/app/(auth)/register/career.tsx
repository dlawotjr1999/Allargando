import React, { useRef, useState } from "react";
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { colors } from "@/constants/theme";
import { useAuth } from "@/contexts/AuthContext";
import { useRegisterForm } from "@/contexts/RegisterContext";
import { ApiError } from "@/lib/apiClient";
import { describeAuthError } from "@/lib/authErrors";
import ScreenHeader from "@/components/common/ScreenHeader";
import StepIndicator from "@/components/common/StepIndicator";
import ThemedButton from "@/components/common/ThemedButton";
import CareerFormItem from "@/components/auth/CareerFormItem";
import { CareerEntry } from "@/types/user";
import {
  CAREER_MAX_COUNT,
  formatPhoneNumber,
  normalizeNickname,
  validateAccountStep,
  validateProfileStep,
} from "@/utils/registerValidation";

// 가입 3단계(활동 이력)와 제출. 제출하면 Firebase 계정을 만들고 서버에 가입한다(AuthContext.registerAccount).
// 성공하면 인증 상태가 바뀌어 루트가 홈으로 보내므로 여기서 직접 이동하지 않는다.
export default function RegisterStep3() {
  const { user, registerAccount } = useAuth();
  const { form, updateForm, resetForm } = useRegisterForm();
  const [submitting, setSubmitting] = useState(false);

  // 배열 index는 항목 삭제 시 뒤 요소가 앞으로 당겨져 재사용되므로,
  // React key로 쓰기 위한 항목별 안정적인 로컬 id를 별도로 관리한다.
  const keyCounter = useRef(0);
  const [careerKeys, setCareerKeys] = useState<number[]>(() =>
    form.careers.map(() => keyCounter.current++)
  );

  const handleChange = (index: number, entry: CareerEntry) => {
    const updated = [...form.careers];
    updated[index] = entry;
    updateForm({ careers: updated });
  };

  const handleRemove = (index: number) => {
    const updated = form.careers.filter((_, i) => i !== index);
    updateForm({ careers: updated });
    setCareerKeys((prev) => prev.filter((_, i) => i !== index));
  };

  const handleAdd = () => {
    if (form.careers.length >= CAREER_MAX_COUNT) {
      Alert.alert("활동 이력", `활동 이력은 최대 ${CAREER_MAX_COUNT}개까지 등록할 수 있어요.`);
      return;
    }
    updateForm({
      careers: [...form.careers, { organization: "", contexts: "" }],
    });
    setCareerKeys((prev) => [...prev, keyCounter.current++]);
  };

  // 가입 제출. 앞 단계 입력을 한 번 더 확인한 뒤(뒤로 가기·이어하기로 비어 있을 수 있어서) 가입한다.
  // 활동 이력은 선택이라 비어도 되고, 단체명·설명이 모두 빈 행은 서버가 버린다(한쪽만 비면 빈 문자열로 저장).
  // 연속 탭으로 계정이 두 번 만들어지지 않도록 submitting으로 잠근다. 실패하면 이 화면에 남아 재시도할 수 있다
  const submit = async (careers: CareerEntry[]) => {
    if (submitting) return;
    // 이어하기(이미 로그인된 계정)는 1단계를 거치지 않았으므로 계정 검증을 건너뛴다
    const invalid = (!user ? validateAccountStep(form) : null) ?? validateProfileStep(form);
    if (invalid) {
      Alert.alert("입력을 확인해 주세요", invalid);
      return;
    }
    setSubmitting(true);
    try {
      await registerAccount({
        email: form.email.trim(),
        password: form.password,
        nickname: normalizeNickname(form.nickname),
        phoneNumber: formatPhoneNumber(form.phoneNumber) ?? form.phoneNumber,
        instrument: form.instrument,
        careers: careers.map((c) => ({ organization: c.organization.trim(), contexts: c.contexts.trim() })),
      });
      resetForm();
    } catch (err) {
      if (err instanceof ApiError) {
        // 닉네임·번호 중복(409)이나 형식 오류(400)는 앞 단계 입력을 고쳐야 한다
        const fixable = err.status === 400 || err.status === 409;
        Alert.alert("가입 실패", fixable ? `${err.message}\n이전 단계에서 수정한 뒤 다시 시도해 주세요.` : err.message);
      } else {
        Alert.alert("가입 실패", describeAuthError(err, "signup"));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleSkip = () => {
    updateForm({ careers: [] });
    submit([]);
  };

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <ScreenHeader
          title="활동 이력"
          subtitle="악기를 연주한 활동이 있다면 추가해주세요 (선택)"
        />
        <StepIndicator total={3} current={3} />

        {form.careers.map((career, index) => (
          <CareerFormItem
            key={careerKeys[index]}
            value={career}
            index={index}
            onChange={handleChange}
            onRemove={handleRemove}
            removable={form.careers.length > 1}
          />
        ))}

        <TouchableOpacity style={styles.addButton} onPress={handleAdd}>
          <Ionicons name="add" size={18} color={colors.textMuted} />
          <Text style={styles.addButtonText}>활동 이력 추가</Text>
        </TouchableOpacity>

        <View style={styles.bottom}>
          <ThemedButton
            title={submitting ? "가입 중..." : "가입 완료"}
            onPress={() => submit(form.careers)}
            disabled={submitting}
          />
          <TouchableOpacity style={styles.skipButton} onPress={handleSkip} disabled={submitting}>
            <Text style={styles.skipText}>건너뛰기</Text>
          </TouchableOpacity>
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
  addButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderWidth: 1,
    borderStyle: "dashed",
    borderColor: colors.border,
    borderRadius: 10,
    height: 44,
    marginBottom: 24,
  },
  addButtonText: {
    fontSize: 13,
    color: colors.textMuted,
  },
  bottom: {
    marginTop: 8,
    marginBottom: 32,
  },
  skipButton: {
    alignItems: "center",
    marginTop: 12,
  },
  skipText: {
    fontSize: 13,
    color: colors.textMuted,
  },
});
