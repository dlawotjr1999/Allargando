// 연주회(Concert) 도메인 타입. 백엔드 API 스펙(GET /api/concerts) 기준.
// 백엔드 ConcertResponseDTO가 목록/단건 공용 단일 DTO라(applicationCount 같은 계산값이 없음),
// 콩쿠르 때의 ContestSummary/ContestDetail 2단 분리 없이 타입 하나로 둔다.
export interface Concert {
  id: number;
  title: string;
  category: string;
  startDate: string; // 공연 시작일, "YYYY-MM-DD"
  endDate: string; // 공연 종료일, "YYYY-MM-DD"
  venue: string;
  region: string;
  posterUrl?: string; // 없는 공연도 있어 선택 필드
  sourceUrl: string; // KOPIS 상세 페이지 링크 — "공연 정보 보기" 버튼 리다이렉트 대상
}
