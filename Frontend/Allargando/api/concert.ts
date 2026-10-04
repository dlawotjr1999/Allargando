// 연주회 조회 API (GET /api/concerts, GET /api/concerts/{id})
import { apiRequest } from "@/lib/apiClient";
import { PageResponse } from "@/types/api";
import { Concert } from "@/types/concert";
import { ConcertFilter } from "@/types/concertFilter";

// 필터+페이지 번호를 쿼리스트링으로 변환. category/region은 반복 키(?category=A&category=B)로
// 보내야 백엔드 @RequestParam List<String>과 맞는다(Spring이 반복 키를 리스트로 바인딩).
// Post 도메인은 아직 mocks 기반이라 이 프로젝트에서 배열 필터를 쿼리스트링으로 만드는 첫 사례.
function buildQuery(filter: ConcertFilter, page: number): string {
  const params = new URLSearchParams();
  filter.categories.forEach((category) => params.append("category", category));
  filter.regions.forEach((region) => params.append("region", region));
  if (filter.fromDate) params.append("fromDate", filter.fromDate);
  if (filter.toDate) params.append("toDate", filter.toDate);
  params.append("page", String(page));
  return params.toString();
}

export function getConcerts(filter: ConcertFilter, page: number) {
  return apiRequest<PageResponse<Concert>>(`/api/concerts?${buildQuery(filter, page)}`);
}

export function getConcert(id: number) {
  return apiRequest<Concert>(`/api/concerts/${id}`);
}
