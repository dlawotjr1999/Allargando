// 모집글 조회·등록·수정·마감·삭제 API
// (GET/POST/PUT/PATCH/DELETE /api/posts, backend/obri/.../post/controller/PostController)
import { apiRequest } from "@/lib/apiClient";
import { PageResponse } from "@/types/api";
import { PostCreateRequest, PostDetail, PostResponse, PostSummary } from "@/types/post";
import { PostFilter } from "@/types/filter";

// 필터+페이지 번호를 쿼리스트링으로 변환.
// - category/instrument/region은 반복 키(?category=A&category=B)로 보내야 백엔드
//   @RequestParam List<String>과 맞는다(Concert 도메인 api/concert.ts와 동일한 패턴).
// - 정렬·상태는 쿼리에 없다: 서버가 항상 최신순(@PageableDefault createdAt DESC)이고, 공개 목록에서
//   CLOSED를 하드코딩으로 제외해 OPEN·PARTIALLY_CLOSED만 노출한다(BACKLOG.md #35). 그래서 PostFilter에도
//   두지 않는다. 마감(CLOSED)한 내 글은 getMyPosts()로만 확인 가능.
function buildQuery(filter: PostFilter, page: number): string {
  const params = new URLSearchParams();
  filter.categories.forEach((category) => params.append("category", category));
  filter.instruments.forEach((instrument) => params.append("instrument", instrument));
  filter.regions.forEach((region) => params.append("region", region));
  if (filter.startDate) params.append("startDate", filter.startDate);
  if (filter.endDate) params.append("endDate", filter.endDate);
  params.append("page", String(page));
  return params.toString();
}

// 모집글 전체 조회 (공개 목록, 무한스크롤). GET이라 자연히 멱등 — 실패 시 그냥 재요청하면 된다.
// 응답은 항상 OPEN·PARTIALLY_CLOSED만 포함(CLOSED 제외는 서버가 강제, 프론트가 걸러낼 필요 없음).
export function getPosts(filter: PostFilter, page: number) {
  return apiRequest<PageResponse<PostSummary>>(`/api/posts?${buildQuery(filter, page)}`);
}

// 내가 올린 모집글 목록(마이페이지). 공개 목록과 달리 status 필터가 아예 없어
// 내가 수동 마감(CLOSED)한 글도 여기서는 그대로 보인다. GET — 멱등.
export function getMyPosts(page: number) {
  return apiRequest<PageResponse<PostSummary>>(`/api/posts/me?page=${page}`);
}

// 모집글 단건 조회. isMine·hasApplied·applicationCount는 요청한 유저 기준으로 서버가 계산해
// 내려주므로, 화면에서 이 값을 그대로 쓰면 되고 별도 mock 목록을 대조해 재계산할 필요가 없다.
// GET — 멱등.
export function getPost(id: number) {
  return apiRequest<PostDetail>(`/api/posts/${id}`);
}

// 모집글 등록. POST라 멱등이 아니다 — 같은 요청을 두 번 보내면 서로 다른 두 글이 생성된다.
// 호출부(등록 버튼)는 응답이 올 때까지 버튼을 비활성화해 연속 탭으로 인한 중복 등록을 막아야 한다.
// 성공 시 악기별 확정 인원(confirmed)이 모두 0인 상태의 PostResponse를 돌려받으므로,
// 등록 직후 화면을 그릴 때 getPost()로 다시 조회할 필요 없이 이 응답을 바로 써도 된다.
export function createPost(payload: PostCreateRequest) {
  return apiRequest<PostResponse>("/api/posts", { method: "POST", body: payload });
}

// 모집글 수정 (본문 전체 교체, 작성자만). PUT이라 멱등 — 같은 payload로 여러 번 호출해도
// 서버 최종 상태는 동일해서 네트워크 재시도가 안전하다. 단 instruments는 "이번에 모집할 전체
// 목록"을 매번 넣어야 한다(부분 추가 아님) — 이름이 같은 항목만 확정 인원·마감 상태를 승계하고,
// 새 목록에서 빠진 이름은 서버에서 삭제된다.
export function updatePost(id: number, payload: PostCreateRequest) {
  return apiRequest<PostResponse>(`/api/posts/${id}`, { method: "PUT", body: payload });
}

// 모집글 수동 전체 마감 (작성자만). PATCH지만 결과 상태 기준으론 멱등하다 — 이미 CLOSED인 글을
// 다시 마감 요청해도 서버가 예외 없이 조용히 성공한다(Post.close()가 플래그만 재설정하는 구조라).
// 그래도 화면에서는 중복 탭 시 불필요한 요청이 나가지 않도록 버튼을 비활성화하는 편이 좋다.
export function closePost(id: number) {
  return apiRequest<void>(`/api/posts/${id}/close`, { method: "PATCH" });
}

// 모집글 삭제 (작성자만, 연관 지원서도 서버에서 함께 정리됨). DELETE지만 멱등은 아니다 —
// 첫 호출은 200으로 성공하지만 이미 삭제된 id에 다시 호출하면 404(NotFoundException)가 난다.
// 재시도 로직을 붙인다면 404는 "이미 삭제된 상태"로 간주해 에러로 취급하지 않는 편이 안전하다.
export function deletePost(id: number) {
  return apiRequest<void>(`/api/posts/${id}`, { method: "DELETE" });
}
