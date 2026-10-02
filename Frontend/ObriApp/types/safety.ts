// 신고·차단 도메인 타입. 백엔드 report·block 패키지의 DTO/enum과 1:1 대응한다.

// 신고 사유 — 백엔드 ReportReason enum과 같은 값
export type ReportReason = "SPAM" | "INAPPROPRIATE" | "FRAUD" | "HARASSMENT" | "OTHER";

// 신고 요청 바디. 백엔드 ReportRequestDTO와 1:1 대응 — 대상은 경로로 지정하므로 사유·설명만 담는다
export interface ReportRequest {
  reason: ReportReason;
  detail?: string;
}

// 신고 대상 — 모집글은 id로, 유저는 화면에 보이는 닉네임(UNIQUE)으로 지정한다
export type ReportTarget =
  | { type: "POST"; postId: number }
  | { type: "USER"; nickname: string };

// 차단 목록 항목. 백엔드 BlockedUserResponseDTO와 1:1 대응
export interface BlockedUser {
  nickname: string;
  instrument: string;
  blockedAt: string;
}
