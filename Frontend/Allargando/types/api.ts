// 무한스크롤 페이지네이션 공통 응답 형태 (CLAUDE.md 4장 — hasNext·currentPage만, totalPages 없음)
export interface PageResponse<T> {
  content: T[];
  hasNext: boolean;
  currentPage: number;
}
