// 연습일지 조회/등록 API (GET·POST /api/practice-logs, GET /api/practice-logs/{id})
// 항상 로그인한 본인 것만 대상이라 apiClient가 기본으로 붙이는 인증 헤더 외에 별도 파라미터가 없다.
import { apiRequest } from "@/lib/apiClient";
import { PageResponse } from "@/types/api";
import { PracticeLogCreateRequest, PracticeLogDetail, PracticeLogSummary } from "@/types/practiceLog";

// 내 연습일지 목록을 페이지 단위(무한스크롤)로 조회. 정렬(logDate DESC)은 백엔드
// @PageableDefault가 고정하므로 프론트에서 별도 정렬 파라미터를 넘기지 않는다.
export function getPracticeLogs(page: number) {
  return apiRequest<PageResponse<PracticeLogSummary>>(`/api/practice-logs?page=${page}`);
}

// 연습일지 단건 상세 조회. 목록 응답(PracticeLogSummary)에는 content가 없어서
// 카드를 눌러 상세를 열 때 이 API로 별도 요청한다.
export function getPracticeLog(id: number) {
  return apiRequest<PracticeLogDetail>(`/api/practice-logs/${id}`);
}

// 연습일지 신규 등록. 성공 시 백엔드가 생성된 전체 레코드(PracticeLogResponseDTO)를 돌려준다.
export function createPracticeLog(payload: PracticeLogCreateRequest) {
  return apiRequest<PracticeLogDetail>("/api/practice-logs", { method: "POST", body: payload });
}

// 연습일지 수정 (전체 필드 교체, 본인 것만). 등록과 동일한 요청 바디를 재사용한다.
export function updatePracticeLog(id: number, payload: PracticeLogCreateRequest) {
  return apiRequest<PracticeLogDetail>(`/api/practice-logs/${id}`, { method: "PUT", body: payload });
}

// 연습일지 삭제 (본인 것만)
export function deletePracticeLog(id: number) {
  return apiRequest<void>(`/api/practice-logs/${id}`, { method: "DELETE" });
}
