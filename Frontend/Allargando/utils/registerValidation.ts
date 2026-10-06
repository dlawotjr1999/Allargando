// 가입·프로필 입력 검증. 순수 함수만 모음(외부 의존 없음).
// 서버가 최종 검증을 하므로 여기서 통과해도 서버가 거절할 수 있다(예: 닉네임 예약어·중복).

export const PASSWORD_MIN = 6; // Firebase 이메일 가입의 최소 길이

// 활동 이력(경력) 입력 상한 — 서버 검증과 같다(단체명·설명 각 255자, 최대 10개)
export const CAREER_MAX_COUNT = 10;
export const CAREER_MAX_LENGTH = 255;

// 서버 NicknamePolicy(D3)와 같은 규칙: 한글(완성형)·영문·숫자·_ 2~20자. 공백·점·이모지·자모 단독은 불허.
// 예약어(me·admin 등)는 목록을 서버가 관리하므로 여기서 걸러내지 않고 서버 응답(400)에 맡긴다
export const NICKNAME_HINT = "한글·영문·숫자·_ 2~20자로 입력해 주세요. 공백·특수문자는 쓸 수 없어요.";
const NICKNAME_RE = /^[가-힣A-Za-z0-9_]{2,20}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// 앞뒤 공백을 지우고 NFC로 정규화한다(서버도 같은 정규화 뒤 검증·저장하므로 보내는 값을 미리 맞춘다)
export function normalizeNickname(nickname: string): string {
  return nickname.trim().normalize("NFC");
}

// 닉네임 형식이 틀렸으면 안내 문구, 맞으면 null. 빈 값은 "아직 안 입력"이라 null(필수 여부는 호출부가 따로 본다)
export function getNicknameError(nickname: string): string | null {
  const value = normalizeNickname(nickname);
  if (!value) return null;
  return NICKNAME_RE.test(value) ? null : NICKNAME_HINT;
}

// 휴대폰 번호를 "010-1234-5678" 형식으로 정규화한다. 하이픈·공백·+82 표기를 받아들이고,
// 휴대폰 번호(010·011·016·017·018·019)가 아니면 null. 서버는 입력 문자열 그대로 UNIQUE 비교를 하므로
// 같은 번호가 표기만 달라 중복 가입되지 않도록 한 가지 형식으로 통일해 보낸다
export function formatPhoneNumber(input: string): string | null {
  let digits = input.replace(/\D/g, "");
  if (digits.startsWith("82") && digits.length >= 11) digits = "0" + digits.slice(2);
  if (!/^01[016789]\d{7,8}$/.test(digits)) return null;
  return digits.length === 11
    ? `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
    : `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
}

// 휴대폰 번호를 Firebase 전화 인증이 요구하는 국제 표기(E.164, 예: +821012345678)로 바꾼다.
// 형식이 올바르지 않으면 null. 앞의 0을 떼고 한국 국가번호 +82를 붙인다
export function toE164(input: string): string | null {
  const formatted = formatPhoneNumber(input);
  if (!formatted) return null;
  return "+82" + formatted.replace(/\D/g, "").slice(1);
}

// 아이디 찾기 결과로 보여 줄 이메일 마스킹. 앞 2자만 남기고 나머지를 *로 가린다(예: test@test.com → te**@test.com)
export function maskEmail(email: string): string {
  const at = email.indexOf("@");
  if (at <= 0) return email;
  const local = email.slice(0, at);
  const visible = local.slice(0, local.length <= 2 ? 1 : 2);
  return `${visible}${"*".repeat(local.length - visible.length)}@${email.slice(at + 1)}`;
}

// 새 비밀번호 검증. 비밀번호 길이 기준은 가입과 같다. 틀린 항목의 안내 문구를 돌려준다(맞으면 null)
export function validateNewPassword(password: string, confirm: string): string | null {
  if (password.length < PASSWORD_MIN) return `비밀번호는 ${PASSWORD_MIN}자 이상 입력해 주세요.`;
  if (password !== confirm) return "비밀번호가 서로 달라요.";
  return null;
}

// 인증번호는 숫자 6자리다(Firebase가 보내는 SMS 코드와 테스트 번호의 고정 코드 모두)
export const PHONE_CODE_LENGTH = 6;

export interface AccountStepValues {
  email: string;
  password: string;
  passwordConfirm: string;
  agreeTerms: boolean;
  agreePrivacy: boolean;
  agreeAge: boolean;
}

// 1단계(계정) 검증. 첫 번째로 틀린 항목의 안내 문구를 돌려준다(모두 맞으면 null)
export function validateAccountStep(values: AccountStepValues): string | null {
  if (!values.email.trim()) return "이메일을 입력해 주세요.";
  if (!EMAIL_RE.test(values.email.trim())) return "이메일 형식을 확인해 주세요.";
  if (values.password.length < PASSWORD_MIN) return `비밀번호는 ${PASSWORD_MIN}자 이상 입력해 주세요.`;
  if (values.password !== values.passwordConfirm) return "비밀번호가 서로 달라요.";
  if (!values.agreeTerms) return "이용약관에 동의해 주세요.";
  if (!values.agreePrivacy) return "개인정보 수집·이용에 동의해 주세요.";
  if (!values.agreeAge) return "만 14세 이상만 가입할 수 있어요.";
  return null;
}

export interface ProfileStepValues {
  nickname: string;
  phoneNumber: string;
  instrument: string;
}

// 2단계(프로필) 검증. 첫 번째로 틀린 항목의 안내 문구를 돌려준다(모두 맞으면 null)
export function validateProfileStep(values: ProfileStepValues): string | null {
  if (!normalizeNickname(values.nickname)) return "닉네임을 입력해 주세요.";
  const nicknameError = getNicknameError(values.nickname);
  if (nicknameError) return nicknameError;
  if (!formatPhoneNumber(values.phoneNumber)) return "휴대폰 번호를 010-0000-0000 형식으로 입력해 주세요.";
  if (!values.instrument) return "악기를 선택해 주세요.";
  return null;
}
