// Firebase Auth 오류를 사용자에게 보여줄 안내 문구로 바꾼다.
// 로그인에서 "계정 없음"과 "비밀번호 틀림"은 일부러 같은 문구로 돌려준다 — Firebase가 이메일 열거 방지로
// 둘을 같은 오류(auth/invalid-credential)로 내려주고, 구분해 알려주면 가입 여부를 추측하는 데 쓰일 수 있기 때문이다.

export type AuthErrorContext = "login" | "signup";

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
      return "이미 가입된 이메일이에요. 로그인해 주세요.";
    case "auth/weak-password":
      return "비밀번호가 너무 약해요. 6자 이상으로 입력해 주세요.";
    case "auth/invalid-credential":
    case "auth/user-not-found":
    case "auth/wrong-password":
      return "이메일 또는 비밀번호가 올바르지 않아요.";
    default:
      return context === "login"
        ? "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
        : "계정을 만들지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
}
