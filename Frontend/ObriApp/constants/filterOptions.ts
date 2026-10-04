import { PostSort } from "@/types/filter";
import { PostStatus } from "@/types/post";

export const CATEGORIES = ["앙상블", "버스킹", "합주", "연주회", "기타"];

// 악기 목록의 단일 소스 — 모집글·필터·지원·가입·프로필 수정이 모두 이 목록을 쓴다.
// 백엔드는 악기명을 문자열 그대로 비교하므로(Post.requireAcceptingInstrument) 화면마다 목록이 다르면
// 가입자의 프로필 악기가 모집 악기에 없어 지원할 수 없게 된다. 항목을 바꾸면 기존 데이터와 어긋나니 추가만 한다.
export const INSTRUMENTS = [
  "바이올린", "비올라", "첼로", "더블베이스",
  "플루트", "오보에", "클라리넷", "바순",
  "호른", "트럼펫", "트롬본", "튜바",
  "피아노", "하프", "타악기",
  "성악", "기타",
];

export const REGIONS = ["서울", "경기", "인천", "부산", "대구", "대전", "광주", "기타"];

// 연주회(KOPIS) 전용 지역·카테고리 — KOPIS의 area/genrenm 값이 REGIONS·CATEGORIES와 표기가 달라 별도로 둠
// (예: REGIONS는 "서울"이지만 KOPIS는 "서울특별시" — Concert.region과 정확히 일치해야 필터가 걸림)
export const CONCERT_REGIONS = [
  "서울특별시", "부산광역시", "대구광역시", "인천광역시", "전남광주통합특별시", "대전광역시", "울산광역시",
  "세종특별자치시", "경기도", "강원특별자치도", "충청북도", "충청남도", "전북특별자치도",
  "경상북도", "경상남도", "제주특별자치도",
];

// KOPIS genrenm 원본 표기 — 백엔드가 이 문자열과 완전 일치로 비교한다. 세 장르(CCCA·CCCC·CCCD) 모두
// 백엔드가 실제 응답으로 확인했다(KopisSyncService). 국악은 "국악"이 아니라 "한국음악(국악)"으로 내려온다
export const CONCERT_CATEGORIES = ["서양음악(클래식)", "한국음악(국악)", "대중음악"];

// 모집글 목록 정렬 3종(D15) — 값은 서버 PostSort와 같다. 순서대로 눌러 바꾼다
export const POST_SORTS: { value: PostSort; label: string }[] = [
  { value: "LATEST", label: "최신순" },
  { value: "EVENT_SOON", label: "공연 임박순" },
  { value: "CLOSING_SOON", label: "마감 임박순" },
];

// 모집 상태 라벨. 필터 칩(모집중·부분 마감·마감)과 카드 뱃지가 함께 쓴다
export const STATUS_LABELS: Record<PostStatus, string> = {
  OPEN: "모집중",
  PARTIALLY_CLOSED: "부분 마감",
  CLOSED: "마감",
};
