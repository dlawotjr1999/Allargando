// 연주회 필터 상태. types/filter.ts(PostFilter)와 같은 패턴이나, Post와 달리 악기·상태 필터가
// 없어 categories/regions/기간과 진행 중 여부만 둔다(백엔드 ConcertSpecification과 1:1 대응).
// fromDate~toDate는 "이 기간에 볼 수 있는 공연" — 공연 기간이 이 기간과 겹치면 나온다(서버 기준).
export interface ConcertFilter {
  // 서버는 여러 장르를 받지만 화면은 하나만 고른다(빈 배열 = 전체)
  categories: string[];
  regions: string[];
  fromDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/concerts의 fromDate 파라미터와 동일
  toDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/concerts의 toDate 파라미터와 동일
  // 진행 중인 공연(시작일 지남·종료 전)만 곧 끝나는 순으로 본다. 끄면 다가오는 공연 먼저(백엔드 running 파라미터)
  running: boolean;
  // 제목·공연장 검색어. 빈 문자열이면 검색하지 않는다(백엔드 keyword 파라미터)
  keyword: string;
}

export const DEFAULT_CONCERT_FILTER: ConcertFilter = {
  categories: [],
  regions: [],
  fromDate: undefined,
  toDate: undefined,
  running: false,
  keyword: "",
};
