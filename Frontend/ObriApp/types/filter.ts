import { PostStatus } from "./post";

// 목록 정렬 — 서버 PostSort와 같은 값이다. 서버는 이 화이트리스트만 받는다(D15, 그 밖의 값은 400)
export type PostSort = "LATEST" | "EVENT_SOON" | "CLOSING_SOON";

export interface PostFilter {
  sort: PostSort;
  categories: string[];
  instruments: string[];
  regions: string[];
  // 보고 싶은 모집 상태. 비어 있으면 서버 기본(모집중·부분마감)이고, "마감"을 고르면 마감된 글도 보인다
  statuses: PostStatus[];
  startDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/posts의 startDate 파라미터와 동일
  endDate?: string; // "YYYY-MM-DD", 백엔드 GET /api/posts의 endDate 파라미터와 동일
}

export const DEFAULT_FILTER: PostFilter = {
  sort: "LATEST",
  categories: [],
  instruments: [],
  regions: [],
  statuses: [],
  startDate: undefined,
  endDate: undefined,
};
