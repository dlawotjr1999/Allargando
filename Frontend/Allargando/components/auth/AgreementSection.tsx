import React from "react";
import { View, StyleSheet } from "react-native";
import { colors } from "@/constants/theme";
import { openLegalDocument, PRIVACY_URL, TERMS_URL } from "@/lib/legal";
import AgreementRow from "@/components/auth/AgreementRow";

interface AgreementValues {
  agreeTerms: boolean;
  agreePrivacy: boolean;
  agreeAge: boolean;
}

interface AgreementSectionProps {
  form: AgreementValues;
  onChange: (fields: Partial<AgreementValues>) => void;
}

// 가입 약관 동의 묶음(전체 동의 + 이용약관·개인정보·만 14세). 1단계(계정)와, 1단계를 거치지 않고
// 이어서 가입하는 경우의 프로필 단계에서 같이 쓴다
export default function AgreementSection({ form, onChange }: AgreementSectionProps) {
  const allAgreed = form.agreeTerms && form.agreePrivacy && form.agreeAge;

  return (
    <View style={styles.agreements}>
      <AgreementRow
        label="전체 동의"
        checked={allAgreed}
        required={false}
        onToggle={() => onChange({ agreeTerms: !allAgreed, agreePrivacy: !allAgreed, agreeAge: !allAgreed })}
      />
      <View style={styles.agreementDivider} />
      <AgreementRow
        label="이용약관 동의"
        checked={form.agreeTerms}
        onToggle={() => onChange({ agreeTerms: !form.agreeTerms })}
        onView={() => openLegalDocument("이용약관", TERMS_URL)}
      />
      <AgreementRow
        label="개인정보 수집·이용 동의"
        checked={form.agreePrivacy}
        onToggle={() => onChange({ agreePrivacy: !form.agreePrivacy })}
        onView={() => openLegalDocument("개인정보처리방침", PRIVACY_URL)}
      />
      <AgreementRow
        label="만 14세 이상입니다"
        checked={form.agreeAge}
        onToggle={() => onChange({ agreeAge: !form.agreeAge })}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  agreements: {
    marginTop: 8,
  },
  agreementDivider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: 4,
  },
});
