// 유저 차단 API (POST/GET/DELETE /api/blocks, backend/allargando/.../block/controller/BlockController)
import { apiRequest } from "@/lib/apiClient";
import { BlockedUser } from "@/types/safety";

// 유저 차단(닉네임으로 지정). 이미 차단한 유저면 서버가 409를 준다 — 호출부는 연속 탭을 막고,
// 409는 "이미 된 상태"라 에러 메시지를 그대로 보여주면 충분하다. 본인 차단은 400.
export function blockUser(nickname: string) {
  return apiRequest<void>("/api/blocks", { method: "POST", body: { nickname } });
}

// 차단 해제. 차단한 적 없는 유저면 404 — 이미 해제된 상태로 보고 목록에서 지워도 안전하다.
export function unblockUser(nickname: string) {
  return apiRequest<void>(`/api/blocks/${encodeURIComponent(nickname)}`, { method: "DELETE" });
}

// 내 차단 목록(최근 차단 순). 목록이 작아 페이지네이션 없이 전체를 받는다. GET — 멱등.
export function getMyBlocks() {
  return apiRequest<BlockedUser[]>("/api/blocks");
}
