import React, { useState } from "react";
import { View, StyleSheet, ScrollView } from "react-native";
import { colors } from "@/constants/theme";
import ScreenHeader from "@/components/common/ScreenHeader";
import Chip from "@/components/common/Chip";
import FindIdSection from "@/components/auth/FindIdSection";
import ResetPasswordByEmailSection from "@/components/auth/ResetPasswordByEmailSection";
import ResetPasswordByPhoneSection from "@/components/auth/ResetPasswordByPhoneSection";

type FindTab = "id" | "password";
type PasswordMethod = "email" | "phone";

// 계정 찾기 화면(로그인 화면에서 진입).
//  · 아이디 찾기: 전화번호 인증 → 가입된 이메일(일부 가림) 표시
//  · 비밀번호 찾기: 이메일(재설정 메일) 또는 전화번호(인증 후 바로 새 비밀번호) 중 하나를 골라 진행
// 탭이나 방법을 바꾸면 이전 영역이 사라지며(입력·인증 상태도 함께 초기화), 전화번호로 들어간 임시 로그인이 있으면
// 각 영역이 사라질 때 로그아웃으로 정리한다.
export default function FindAccountScreen() {
  const [tab, setTab] = useState<FindTab>("id");
  const [method, setMethod] = useState<PasswordMethod>("email");

  return (
    <View style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        <ScreenHeader title="계정 찾기" subtitle="아이디나 비밀번호를 잊으셨나요?" />

        <View style={styles.chipRow}>
          <Chip label="아이디 찾기" active={tab === "id"} onPress={() => setTab("id")} />
          <Chip label="비밀번호 찾기" active={tab === "password"} onPress={() => setTab("password")} />
        </View>

        {tab === "id" ? (
          <FindIdSection onGoPasswordTab={() => setTab("password")} />
        ) : (
          <View>
            <View style={styles.chipRow}>
              <Chip label="이메일로 찾기" active={method === "email"} onPress={() => setMethod("email")} />
              <Chip label="전화번호로 찾기" active={method === "phone"} onPress={() => setMethod("phone")} />
            </View>
            {method === "email" ? <ResetPasswordByEmailSection /> : <ResetPasswordByPhoneSection />}
          </View>
        )}
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
    paddingBottom: 40,
  },
  chipRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 20,
  },
});
