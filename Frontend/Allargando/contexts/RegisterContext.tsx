import React, { createContext, useContext, useState } from "react";
import { CareerEntry } from "@/types/user";

interface RegisterForm {
  email: string;
  password: string;
  passwordConfirm: string;
  // 약관 동의(1단계). Play UGC 정책상 가입 전에 이용약관·개인정보 동의를 받아야 한다
  agreeTerms: boolean;
  agreePrivacy: boolean;
  agreeAge: boolean;
  name: string;
  nickname: string;
  phoneNumber: string;
  instrument: string;
  careers: CareerEntry[];
}

interface RegisterContextType {
  form: RegisterForm;
  updateForm: (fields: Partial<RegisterForm>) => void;
  resetForm: () => void;
}

const initialForm: RegisterForm = {
  email: "",
  password: "",
  passwordConfirm: "",
  agreeTerms: false,
  agreePrivacy: false,
  agreeAge: false,
  name: "",
  nickname: "",
  phoneNumber: "",
  instrument: "",
  careers: [{ organization: "", contexts: "" }],
};

const RegisterContext = createContext<RegisterContextType | null>(null);

export function RegisterProvider({ children }: { children: React.ReactNode }) {
  const [form, setForm] = useState<RegisterForm>(initialForm);

  const updateForm = (fields: Partial<RegisterForm>) => {
    setForm((prev) => ({ ...prev, ...fields }));
  };

  const resetForm = () => setForm(initialForm);

  return (
    <RegisterContext.Provider value={{ form, updateForm, resetForm }}>
      {children}
    </RegisterContext.Provider>
  );
}

export function useRegisterForm() {
  const context = useContext(RegisterContext);
  if (!context) {
    throw new Error("useRegisterForm must be used within RegisterProvider");
  }
  return context;
}
