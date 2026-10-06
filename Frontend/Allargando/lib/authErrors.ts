// Firebase Auth 오류를 사용자에게 보여줄 안내 문구로 바꾼다.
// 로그인에서 "계정 없음"과 "비밀번호 틀림"은 일부러 같은 문구로 돌려준다 — Firebase가 이메일 열거 방지로
// 둘을 같은 오류(auth/invalid-credential)로 내려주고, 구분해 알려주면 가입 여부를 추측하는 데 쓰일 수 있기 때문이다.

export type AuthErrorContext = "login" | "signup" | "phone";

// 오류 객체에서 Firebase 오류 코드(예: "auth/invalid-email")를 꺼낸다. 없으면 null
function errorCode(err: unknown): string | null {
  if (typeof err === "object" && err !== null && "code" in err) {
    const code = (err as { code: unknown }).code;
    return typeof code === "string" ? code : null;
  }
  return null;
}

// context는 같은 코드라도 문구가 달라지는 경우(예: 이메일 형식 오류)를 위해 받는다
export function describeAuthError(err: unknown, context: AuthErrorContext): string {
  switch (errorCode(err)) {
    case "auth/invalid-email":
      return "이메일 형식을 확인해 주세요.";
    case "auth/network-request-failed":
      return "네트워크 연결을 확인한 뒤 다시 시도해 주세요.";
    case "auth/too-many-requests":
      return "시도가 너무 많아요. 잠시 후 다시 시도해 주세요.";
    case "auth/user-disabled":
      return "사용이 정지된 계정이에요.";
    case "auth/email-already-in-use":
      return context === "signup"
        ? "이미 가입된 이메일이에요. 이전 단계에서 다른 이메일을 입력하거나, 이미 계정이 있다면 로그인해 주세요."
        : "이미 가입된 이메일이에요. 로그인해 주세요.";
    case "auth/account-not-found":
      return "이 전화번호로 가입된 계정을 찾지 못했어요.";
    case "auth/signup-incomplete":
      return "가입을 마치지 못한 전화번호예요. 회원가입에서 이어서 진행해 주세요.";
    case "auth/requires-recent-login":
      return "보안을 위해 다시 인증이 필요해요. 처음부터 다시 시도해 주세요.";
    case "auth/phone-already-registered":
      return "이미 가입된 전화번호예요. 로그인 화면에서 이메일로 로그인해 주세요.";
    case "auth/phone-not-verified":
      return "전화번호 인증을 먼저 완료해 주세요.";
    case "auth/weak-password":
      return "비밀번호가 너무 약해요. 6자 이상으로 입력해 주세요.";
    case "auth/invalid-phone-number":
      return "휴대폰 번호를 010-0000-0000 형식으로 입력해 주세요.";
    case "auth/invalid-verification-code":
      return "인증번호가 올바르지 않아요. 다시 확인해 주세요.";
    case "auth/code-expired":
    case "auth/session-expired":
      return "인증번호가 만료됐어요. 인증번호를 다시 받아 주세요.";
    case "auth/quota-exceeded":
      return "오늘 보낼 수 있는 인증번호 한도를 넘었어요. 내일 다시 시도해 주세요.";
    case "auth/captcha-check-failed":
    case "auth/missing-client-identifier":
      return "앱 확인에 실패했어요. 앱을 최신 버전으로 업데이트한 뒤 다시 시도해 주세요.";
    case "auth/credential-already-in-use":
    case "auth/account-exists-with-different-credential":
      return "이미 다른 계정에 사용 중인 전화번호예요.";
    case "auth/provider-already-linked":
      return "이미 인증이 끝난 계정이에요.";
    case "auth/invalid-credential":
    case "auth/user-not-found":
    case "auth/wrong-password":
      return "이메일 또는 비밀번호가 올바르지 않아요.";
    default:
      if (context === "phone") return "전화번호를 인증하지 못했어요. 잠시 후 다시 시도해 주세요.";
      return context === "login"
        ? "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
        : "계정을 만들지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
}
