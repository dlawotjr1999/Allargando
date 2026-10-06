// 가입·FCM 토큰 API (POST /api/auth/register, PATCH·DELETE /api/auth/fcm-token,
// backend/allargando/.../auth/controller/AuthController)
import { apiRequest } from "@/lib/apiClient";
import { CareerEntry } from "@/types/user";

// 가입 요청 바디. 백엔드 RegisterRequestDTO와 대응 — 이메일·UID·전화번호는 ID 토큰에서 취하고 여기엔 프로필만 담는다.
export interface RegisterRequest {
  nickname: string;
  instrument: string;
  careers: CareerEntry[];
}

// 가입(전화 인증과 이메일 연결을 마친 계정의 토큰으로 호출). 서버가 멱등이라 같은 토큰으로 재시도해도 안전하다 —
// 이미 가입된 UID면 409가 아니라 기존 결과를 돌려준다. 가입 직후라 아직 서버에 유저가 없으므로
// 401을 만나도 로그아웃시키지 않는다(가입 도중 세션이 사라지면 안 됨).
export function registerUser(payload: RegisterRequest) {
  return apiRequest<{ createdAt: string }>("/api/auth/register", {
    method: "POST",
    body: payload,
    handleUnauthorized: false,
  });
}

// FCM 토큰 등록·갱신. 로그인 후 이 기기의 푸시 토큰을 내 계정에 연결한다. 같은 토큰을 가진 다른 계정의 값은 서버가 먼저
// 비우므로(토큰은 기기에 속한다), 한 기기에서 계정을 바꿔 로그인해도 이전 계정으로 알림이 가지 않는다. 같은 값을 다시 보내도 안전하다.
export function registerFcmToken(fcmToken: string) {
  return apiRequest<void>("/api/auth/fcm-token", { method: "PATCH", body: { fcmToken } });
}

// FCM 토큰 해제. 토큰은 기기에 속하므로 로그아웃·탈퇴 직전에 불러야 같은 기기의 다음 계정에 푸시가 가지 않는다.
// 푸시 토큰을 등록한 적이 없어도(서버 값이 null) 호출은 성공한다. DELETE지만 결과 기준으로 멱등.
export function clearFcmToken() {
  return apiRequest<void>("/api/auth/fcm-token", { method: "DELETE" });
}
