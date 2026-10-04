// 연주회 필터 상태. types/filter.ts(PostFilter)와 같은 패턴이나, Post와 달리 악기·상태 필터가
// 없어 categories/regions/기간 3가지만 둔다(백엔드 ConcertSpecification과 1:1 대응).
export interface ConcertFilter {
  categories: string[];
  regions: string[];
  fromDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/concerts의 fromDate 파라미터와 동일
  toDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/concerts의 toDate 파라미터와 동일
}

export const DEFAULT_CONCERT_FILTER: ConcertFilter = {
  categories: [],
  regions: [],
  fromDate: undefined,
  toDate: undefined,
};
