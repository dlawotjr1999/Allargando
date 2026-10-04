// 모집글(Post) 도메인 타입. 백엔드 PostSummaryResponseDTO/PostDetailResponseDTO/PostResponseDTO 기준
// (backend/allargando/.../post/dto)으로 필드를 1:1 맞춘다. 화면·컴포넌트·더미데이터가 공유하는 단일 소스.
import type { ApplicationStatus } from "./application";

export type PostStatus = "OPEN" | "PARTIALLY_CLOSED" | "CLOSED";

// 백엔드 PostInstrumentDTO와 1:1 대응. confirmed는 수락 완료된 인원(=지원 수락 시 서버가 증가),
// closed는 해당 악기 정원이 찼는지 여부(confirmed >= people일 때 서버가 true로 계산해 내려줌 —
// 프론트에서 다시 계산하지 않고 이 값을 그대로 신뢰한다).
export interface PostInstrument {
  id: number;
  instrument: string;
  people: number;
  confirmed: number;
  closed: boolean;
}

// 목록(GET /api/posts, GET /api/posts/me)에서 카드로 보여줄 때 필요한 필드.
// 백엔드 PostSummaryResponseDTO와 1:1 대응.
export interface PostSummary {
  id: number;
  title: string;
  category: string;
  eventAt: string; // ISO 8601 (예: "2026-08-01T14:00:00")
  location: string;
  region: string;
  instruments: PostInstrument[];
  timetable: string;
  status: PostStatus;
}

// 단건 조회 응답에 중첩되는 작성자 요약. 백엔드 PostDetailResponseDTO.Writer와 1:1 대응.
// 매너 점수(REVIEWS 테이블) 같은 평판 필드는 백엔드에 아직 없는 향후 기능이라 여기 없음 — 임의로
// 추가하면 실 데이터 연결 시 항상 undefined가 되므로 타입에 없는 게 맞다.
export interface PostWriter {
  nickname: string;
  instrument: string;
}

// 단건 조회(GET /api/posts/{id}) 응답. 백엔드 PostDetailResponseDTO와 1:1 대응.
// isMine·hasApplied·applicationCount는 요청한 유저 기준으로 서버가 계산해 내려주는 값이므로,
// 화면에서 별도 목록(내 글 id 목록·내 지원 목록 등)을 대조해 프론트가 다시 계산하지 않는다.
export interface PostDetail extends PostSummary {
  writer: PostWriter;
  applicationCount: number;
  isMine: boolean;
  // 지원한 적이 있는지 — myApplicationStatus가 있는지와 같다(호환용). 버튼 문구는 myApplicationStatus로 가른다
  hasApplied: boolean;
  // 내 지원 상태(지원한 적 없으면 null). 취소(CANCELLED)한 지원만 다시 지원할 수 있다
  myApplicationStatus: ApplicationStatus | null;
  // 작성자가 수동으로 마감했는지 — 작성자에게만 내려오고 남의 글은 null. "모집 재개" 메뉴를 수동 마감 글에만 보이는 데 쓴다
  // (정원이 차서 자동 마감된 글은 false라 재개 대상이 아니다)
  manuallyClosed: boolean | null;
  description?: string;
  createdAt: string;
}

// 모집글 등록(POST /api/posts)·수정(PUT /api/posts/{id}) 공용 요청 바디.
// 백엔드 PostCreateRequestDTO와 1:1 대응 — 수정 시 instruments는 "이번에 모집할 전체 목록"이어야
// 한다(부분 추가 아님, 이름이 같은 항목만 확정 인원·마감 상태를 승계하고 나머지는 갈아끼워짐).
export interface PostCreateRequest {
  category: string;
  title: string;
  eventAt: string;
  location: string;
  region: string;
  timetable: string;
  description?: string;
  instruments: { instrument: string; people: number }[];
}

// 등록·수정 응답. 백엔드 PostResponseDTO와 1:1 대응 — 목록/상세 전용 필드(writer·isMine 등) 없음.
export interface PostResponse extends PostSummary {
  description?: string;
  createdAt: string;
}
