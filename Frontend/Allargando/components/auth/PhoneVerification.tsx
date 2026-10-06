import React, { useEffect, useState } from "react";
import { useAuth } from "@/contexts/AuthContext";
import { formatPhoneNumber } from "@/utils/registerValidation";
import PhoneCodeForm from "@/components/auth/PhoneCodeForm";

interface PhoneVerificationProps {
  phoneNumber: string;
  onChangePhone: (phoneNumber: string) => void;
}

// 가입 화면의 전화번호 입력 + SMS 인증번호 확인 영역.
// 확인에 성공하면 번호 입력칸이 잠기며 "인증 완료"로 바뀐다. 인증된 번호는 Firebase 로그인 계정에 기록되므로
// (user.phoneNumber) 화면을 다시 열어도 유지된다.
export default function PhoneVerification({ phoneNumber, onChangePhone }: PhoneVerificationProps) {
  const { user, sendPhoneCode, confirmPhoneCode } = useAuth();
  // 확인 직후 로그인 상태(user)가 갱신되기 전까지 "인증 완료"를 바로 보여주기 위한 표시
  const [justVerified, setJustVerified] = useState(false);

  // 이미 인증된 계정(가입을 마치지 못하고 돌아온 경우)이면 입력칸에 그 번호를 채워 보여 준다
  useEffect(() => {
    if (user?.phoneNumber && !phoneNumber) {
      onChangePhone(formatPhoneNumber(user.phoneNumber) ?? user.phoneNumber);
    }
  }, [user?.phoneNumber, phoneNumber, onChangePhone]);

  return (
    <PhoneCodeForm
      phoneNumber={phoneNumber}
      onChangePhone={onChangePhone}
      sendCode={sendPhoneCode}
      confirmCode={confirmPhoneCode}
      onVerified={() => setJustVerified(true)}
      verified={justVerified || !!user?.phoneNumber}
      hint="모집글에 지원하면 모집자에게 공개돼요. 문자로 받은 인증번호로 본인 확인을 해요."
    />
  );
}
