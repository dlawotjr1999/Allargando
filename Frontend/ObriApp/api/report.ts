// 신고 API (POST /api/reports/..., backend/obri/.../report/controller/ReportController)
import { apiRequest } from "@/lib/apiClient";
import { ReportRequest, ReportTarget } from "@/types/safety";

// 모집글 또는 유저 신고. POST지만 같은 대상을 다시 신고하면 서버가 409("이미 신고한 …")로 막아
// 중복 접수는 생기지 않는다. 호출부는 응답이 올 때까지 제출 버튼을 잠가 연속 탭을 막는다.
// 본인 글·본인 신고는 400, 없는 대상은 404.
export function submitReport(target: ReportTarget, payload: ReportRequest) {
  const path =
    target.type === "POST"
      ? `/api/reports/posts/${target.postId}`
      : `/api/reports/users/${encodeURIComponent(target.nickname)}`;
  return apiRequest<void>(path, { method: "POST", body: payload });
}
