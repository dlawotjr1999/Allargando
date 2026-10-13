// 가입·프로필 입력 검증. 순수 함수만 모음(외부 의존 없음).
// 서버가 최종 검증을 하므로 여기서 통과해도 서버가 거절할 수 있다(예: 닉네임 예약어·중복).

export const PASSWORD_MIN = 8; // Firebase의 최소는 6자이지만 앱에서는 8자로 받는다

// 활동 이력(경력) 입력 상한 — 서버 검증과 같다(단체명·설명 각 255자, 최대 10개)
export const CAREER_MAX_COUNT = 10;
export const CAREER_MAX_LENGTH = 255;

// 서버 NicknamePolicy(D3)와 같은 규칙: 한글(완성형)·영문·숫자·_ 2~20자. 공백·점·이모지·자모 단독은 불허.
// 예약어(me·admin 등)는 목록을 서버가 관리하므로 여기서 걸러내지 않고 서버 응답(400)에 맡긴다
export const NICKNAME_HINT = "한글·영문·숫자·_ 2~20자로 입력해 주세요. 공백·특수문자는 쓸 수 없어요.";
const NICKNAME_RE = /^[가-힣A-Za-z0-9_]{2,20}$/;

// 서버 NamePolicy와 같은 규칙: 한글(완성형)·영문과 단어 사이 공백 한 칸, 2~30자. 숫자·기호·이모지는 불허.
export const NAME_HINT = "지원할 때 모집자에게만 보여요. 한글 또는 영문 2~30자로 입력해 주세요.";
export const NAME_FORMAT_MESSAGE = "이름은 한글 또는 영문 2~30자로 입력해 주세요.";
const NAME_RE = /^[가-힣A-Za-z]+(?: [가-힣A-Za-z]+)*$/;

// ---- 아이디 ------------------------------------------------------------------------------------------
// 로그인 아이디. Firebase는 이메일 형식의 로그인만 받으므로 아이디를 가짜 이메일로 바꿔 쓴다("violin_kim" →
// "violin_kim@allargando.invalid"). 사용자는 가짜 이메일을 보지 못한다. 이메일을 수집하지 않으므로 서버에도 저장하지 않는다.
// 이 도메인은 한 번 가입이 생기면 바꿀 수 없다 — 바꾸면 이미 가입한 사람이 로그인하지 못한다. 첫 빌드 전에 개발 프로젝트에서
// Firebase가 이 도메인의 주소로 가입을 받아 주는지 확인하고, 받지 않으면 여기 한 곳만 고친다
export const LOGIN_ID_EMAIL_DOMAIN = "allargando.invalid";

export const LOGIN_ID_HINT = "영문 소문자로 시작하고, 영문 소문자·숫자·_ 4~20자로 입력해 주세요.";
const LOGIN_ID_RE = /^[a-z][a-z0-9_]{3,19}$/;
// 닉네임 예약어와 같은 취지 — 운영을 사칭하거나 시스템 계정처럼 보이는 아이디를 막는다(소문자로 비교)
const LOGIN_ID_RESERVED = new Set([
  "me", "admin", "administrator", "root", "system", "support", "official",
  "allargando", "관리자", "운영자",
]);

// 앞뒤 공백을 지우고 소문자로 맞춘다(Firebase는 이메일을 소문자로 다루므로 입력도 같게 둔다)
export function normalizeLoginId(loginId: string): string {
  return loginId.trim().toLowerCase();
}

// 아이디 형식이 틀렸으면 안내 문구, 맞으면 null. 빈 값은 "아직 안 입력"이라 null(필수 여부는 호출부가 따로 본다)
export function getLoginIdError(loginId: string): string | null {
  const value = normalizeLoginId(loginId);
  if (!value) return null;
  if (!LOGIN_ID_RE.test(value)) return LOGIN_ID_HINT;
  if (LOGIN_ID_RESERVED.has(value)) return "사용할 수 없는 아이디예요.";
  return null;
}

// 아이디 → Firebase 로그인에 쓰는 가짜 이메일
export function loginIdToEmail(loginId: string): string {
  return `${normalizeLoginId(loginId)}@${LOGIN_ID_EMAIL_DOMAIN}`;
}

// 가짜 이메일 → 아이디. 이 앱이 만든 형식이 아니면(예전 이메일 계정 등) null
export function emailToLoginId(email: string | null | undefined): string | null {
  if (!email) return null;
  const suffix = `@${LOGIN_ID_EMAIL_DOMAIN}`;
  return email.endsWith(suffix) ? email.slice(0, -suffix.length) : null;
}

// 앞뒤 공백을 지우고 NFC로 정규화한다(서버도 같은 정규화 뒤 검증·저장하므로 보내는 값을 미리 맞춘다)
export function normalizeNickname(nickname: string): string {
  return nickname.trim().normalize("NFC");
}

// 앞뒤 공백을 지우고 NFC로 정규화한다(서버도 같은 정규화 뒤 검증·저장한다)
export function normalizeName(name: string): string {
  return name.trim().normalize("NFC");
}

// 이름 형식이 틀렸으면 안내 문구, 맞으면 null. 빈 값은 "아직 안 입력"이라 null(필수 여부는 호출부가 따로 본다)
export function getNameError(name: string): string | null {
  const value = normalizeName(name);
  if (!value) return null;
  return value.length >= 2 && value.length <= 30 && NAME_RE.test(value) ? null : NAME_FORMAT_MESSAGE;
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

// 새 비밀번호 검증. 비밀번호 길이 기준은 가입과 같다. 틀린 항목의 안내 문구를 돌려준다(맞으면 null)
export function validateNewPassword(password: string, confirm: string): string | null {
  if (password.length < PASSWORD_MIN) return `비밀번호는 ${PASSWORD_MIN}자 이상 입력해 주세요.`;
  if (password !== confirm) return "비밀번호가 서로 달라요.";
  return null;
}

// 인증번호는 숫자 6자리다(Firebase가 보내는 SMS 코드와 테스트 번호의 고정 코드 모두)
export const PHONE_CODE_LENGTH = 6;

export interface AccountStepValues {
  loginId: string;
  password: string;
  passwordConfirm: string;
  agreeTerms: boolean;
  agreePrivacy: boolean;
  agreeAge: boolean;
}

// 1단계(계정) 검증. 첫 번째로 틀린 항목의 안내 문구를 돌려준다(모두 맞으면 null)
export function validateAccountStep(values: AccountStepValues): string | null {
  if (!normalizeLoginId(values.loginId)) return "아이디를 입력해 주세요.";
  const loginIdError = getLoginIdError(values.loginId);
  if (loginIdError) return loginIdError;
  if (values.password.length < PASSWORD_MIN) return `비밀번호는 ${PASSWORD_MIN}자 이상 입력해 주세요.`;
  if (values.password !== values.passwordConfirm) return "비밀번호가 서로 달라요.";
  if (!values.agreeTerms) return "이용약관에 동의해 주세요.";
  if (!values.agreePrivacy) return "개인정보 수집·이용에 동의해 주세요.";
  if (!values.agreeAge) return "만 14세 이상만 가입할 수 있어요.";
  return null;
}

export interface ProfileStepValues {
  name: string;
  nickname: string;
  phoneNumber: string;
  instrument: string;
}

// 2단계(프로필) 검증. 첫 번째로 틀린 항목의 안내 문구를 돌려준다(모두 맞으면 null)
export function validateProfileStep(values: ProfileStepValues): string | null {
  if (!normalizeName(values.name)) return "이름을 입력해 주세요.";
  const nameError = getNameError(values.name);
  if (nameError) return nameError;
  if (!normalizeNickname(values.nickname)) return "닉네임을 입력해 주세요.";
  const nicknameError = getNicknameError(values.nickname);
  if (nicknameError) return nicknameError;
  if (!formatPhoneNumber(values.phoneNumber)) return "휴대폰 번호를 010-0000-0000 형식으로 입력해 주세요.";
  if (!values.instrument) return "악기를 선택해 주세요.";
  return null;
}

export interface ConsentValues {
  agreeTerms: boolean;
  agreePrivacy: boolean;
  agreeAge: boolean;
}

// 약관 동의 검증. 가입 제출 직전에 한 번 더 확인한다 — 이어하기(이미 로그인된 계정)는 1단계의 동의 화면을
// 거치지 않아 이 값이 비어 있을 수 있고, 서버는 동의 없이는 가입시키지 않는다
export function validateConsent(values: ConsentValues): string | null {
  if (!values.agreeTerms) return "이용약관에 동의해 주세요.";
  if (!values.agreePrivacy) return "개인정보 수집·이용에 동의해 주세요.";
  if (!values.agreeAge) return "만 14세 이상만 가입할 수 있어요.";
  return null;
}
