// 모집글 목록 필터. 정렬·상태는 서버가 받지 않아(항상 최신순, OPEN·PARTIALLY_CLOSED만 노출) 두지 않는다.
// 정렬 3종·상태 칩은 백엔드 POST-T16~T19가 끝나면 이 타입과 쿼리(api/post.ts)에 함께 추가한다.
export interface PostFilter {
  categories: string[];
  instruments: string[];
  regions: string[];
  startDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/posts의 startDate 파라미터와 동일
  endDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/posts의 endDate 파라미터와 동일
}

export const DEFAULT_FILTER: PostFilter = {
  categories: [],
  instruments: [],
  regions: [],
  startDate: undefined,
  endDate: undefined,
};

