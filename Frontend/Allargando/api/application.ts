// 지원 제출·조회·상태 전이 API
// (GET/POST/PATCH /api/applications, backend/allargando/.../application/controller/ApplicationController)
import { apiRequest } from "@/lib/apiClient";
import { PageResponse } from "@/types/api";
import { ApplicationCreateRequest, ApplicationSummary } from "@/types/application";

// 지원서 제출. POST라 멱등이 아니지만, 백엔드가 (post_id, user_id) UNIQUE 제약 +
// existsByPostIdAndUserId 사전 체크를 두고 있어 중복 제출은 새 행이 아니라 409 Conflict로 막힌다.
// 그래도 화면에서 연속 탭 자체는 막아야 한다 — 두 요청이 거의 동시에 도착하면 하나는 정상 처리되고
// 하나는 사용자에게 409 에러로 노출되어 불필요한 실패 경험을 주기 때문.
export function submitApplication(payload: ApplicationCreateRequest) {
  return apiRequest<ApplicationSummary>("/api/applications/submit", {
    method: "POST",
    body: payload,
  });
}

// 내 지원 목록(마이페이지). GET — 멱등. size는 페이지 크기(서버 상한 50)
export function getMyApplications(page: number, size?: number) {
  const sizeParam = size ? `&size=${size}` : "";
  return apiRequest<PageResponse<ApplicationSummary>>(`/api/applications/me?page=${page}${sizeParam}`);
}

// 지원서 단건 조회 (모집자 또는 지원자 본인만 200, 그 외 403). GET — 멱등.
export function getApplication(id: number) {
  return apiRequest<ApplicationSummary>(`/api/applications/${id}`);
}

// 한 모집글에 대한 지원자 목록 (모집자용, 무한스크롤). GET — 멱등.
export function getApplicationsByPostId(postId: number, page: number) {
  return apiRequest<PageResponse<ApplicationSummary>>(`/api/applications/post/${postId}?page=${page}`);
}

// 지원 수락 (모집자, PENDING → ACCEPTED). PATCH지만 멱등이 아니다 — 이미 처리된 지원서에 다시
// 호출하면 서버가 400(BadRequestException: "대기 중인 지원서만 처리할 수 있습니다")을 던진다.
// 화면에서는 accept 성공 직후 버튼을 즉시 비활성화해 재탭으로 인한 불필요한 400을 막아야 한다.
export function acceptApplication(id: number) {
  return apiRequest<void>(`/api/applications/${id}/accept`, { method: "PATCH" });
}

// 지원 거절 (모집자, PENDING → REJECTED). accept와 동일하게 PENDING에서만 허용 — 재호출 시 400.
export function rejectApplication(id: number) {
  return apiRequest<void>(`/api/applications/${id}/reject`, { method: "PATCH" });
}

// 지원 취소 (지원자 본인, PENDING → CANCELLED). PENDING에서만 허용 — 이미 수락/거절/취소된
// 지원서에 다시 호출하면 400("대기 중인 지원만 취소할 수 있습니다").
export function cancelApplication(id: number) {
  return apiRequest<void>(`/api/applications/${id}/cancel`, { method: "PATCH" });
}

// 수락 철회 (모집자, ACCEPTED → REVOKED — 확정 취소·해당 악기 자리 재오픈). ACCEPTED에서만 허용 —
// 이미 철회됐거나 애초에 수락되지 않은 지원서에 호출하면 400("수락된 지원만 철회할 수 있습니다").
export function revokeApplication(id: number) {
  return apiRequest<void>(`/api/applications/${id}/revoke`, { method: "PATCH" });
}
