// 연습일지(PracticeLog) 도메인 타입. 백엔드 API 스펙(GET/POST /api/practice-logs) 기준.
// 화면·컴포넌트가 공유하는 단일 소스.

// 목록 조회에서 카드로 보여줄 때 필요한 최소 필드 (PracticeLogSummaryResponseDTO).
// content는 목록에 포함되지 않으므로 상세 조회(PracticeLogDetail)에서 확장.
export interface PracticeLogSummary {
  id: number;
  title: string;
  logDate: string; // "YYYY-MM-DD"
  duration: number; // 연습 시간(분)
}

// 단건 조회·등록·수정 응답 (PracticeLogResponseDTO).
export interface PracticeLogDetail extends PracticeLogSummary {
  content: string;
  createdAt: string;
  updatedAt: string;
}

// 등록/수정 요청 바디 (PracticeLogCreateRequestDTO — 백엔드가 POST/PUT에 동일 DTO를 재사용).
// 현재 프론트는 등록(POST)만 구현했지만, 추후 수정 화면을 추가해도 이 타입을 그대로 쓸 수 있다.
export interface PracticeLogCreateRequest {
  title: string;
  logDate: string; // "YYYY-MM-DD"
  duration: number; // 연습 시간(분), 1 이상 (백엔드 @Min(1))
  content?: string; // 선택 입력 — 비우면 undefined로 보내 백엔드에 필드 자체를 생략
}
