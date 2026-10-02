// 내 정보 조회·수정·탈퇴, 닉네임 중복 확인 API
// (GET/PUT/DELETE /api/users/me, GET /api/users/check/{nickname}, backend/obri/.../user/controller/UserController)
import { apiRequest } from "@/lib/apiClient";
import { CareerEntry, UserProfile } from "@/types/user";

// 내 정보 수정(PUT /api/users/me) 요청 바디. 백엔드 UserUpdateRequestDTO와 대응 —
// careers는 부분 수정이 아니라 "전체 교체"(서버가 기존 경력을 전부 지우고 다시 저장)라 항상 전체 목록을 보낸다.
export interface UserUpdateRequest {
  nickname: string;
  instrument: string;
  careers: CareerEntry[];
}

// 내 정보 조회. GET이라 멱등.
export function getMyInfo() {
  return apiRequest<UserProfile>("/api/users/me");
}

// 내 정보 수정. PUT이라 멱등 — 같은 payload를 여러 번 보내도 최종 상태가 같아 재시도가 안전하다.
export function updateMyInfo(payload: UserUpdateRequest) {
  return apiRequest<UserProfile>("/api/users/me", { method: "PUT", body: payload });
}

// 회원 탈퇴. 멱등이 아니다 — 성공 후 같은 토큰으로 다시 호출하면 유저가 없어 실패한다.
// 서버가 유저 데이터를 지우므로 호출부는 성공하면 곧바로 로그아웃(Firebase signOut)해야 한다.
export function deleteMyAccount() {
  return apiRequest<void>("/api/users/me", { method: "DELETE" });
}

// 닉네임 중복 여부(true면 이미 사용 중). 인증 없이 호출 가능한 경로(회원가입 중에도 쓰임).
// 한글 닉네임이 경로에 들어가므로 인코딩한다.
export async function isNicknameDuplicated(nickname: string) {
  const result = await apiRequest<{ isDuplicated: boolean }>(
    `/api/users/check/${encodeURIComponent(nickname)}`,
    { requiresAuth: false }
  );
  return result.isDuplicated;
}
