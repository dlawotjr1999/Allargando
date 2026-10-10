package com.wangnu.allargando.post.controller;

import com.wangnu.allargando.global.ratelimit.RateLimit;
import com.wangnu.allargando.global.common.APIResponse;
import com.wangnu.allargando.global.common.PageSupport;
import com.wangnu.allargando.global.common.PageResponse;
import com.wangnu.allargando.post.dto.PostCreateRequestDTO;
import com.wangnu.allargando.post.dto.PostDetailResponseDTO;
import com.wangnu.allargando.post.dto.PostResponseDTO;
import com.wangnu.allargando.post.dto.PostSummaryResponseDTO;
import com.wangnu.allargando.post.entity.PostStatus;
import com.wangnu.allargando.post.repository.PostSort;
import com.wangnu.allargando.post.service.PostService;
import com.wangnu.allargando.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 모집글 관련 API 컨트롤러
 * POST   /api/posts             — 모집글 등록
 * GET    /api/posts             — 모집글 전체 조회 (필터·정렬·상태·페이지네이션)
 * GET    /api/posts/{id}        — 모집글 단건 조회
 * PUT    /api/posts/{id}        — 모집글 수정 (작성자만)
 * PATCH  /api/posts/{id}/close  — 모집글 수동 전체 마감 (작성자만)
 * PATCH  /api/posts/{id}/reopen — 모집글 수동 마감 해제(모집 재개, 작성자만)
 * DELETE /api/posts/{id}        — 모집글 삭제 (작성자만)
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 모집글 등록 (등록 성공 시 전체 broadcast 알림)
    @RateLimit(limit = 10, windowSeconds = 3600)  // 등록마다 전체 푸시가 나가므로 시간당 10건
    @PostMapping
    public ResponseEntity<APIResponse<PostResponseDTO>> createPost(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid PostCreateRequestDTO request) {
        PostResponseDTO response = postService.createPost(user, request);
        return ResponseEntity.ok(APIResponse.ok("모집글이 등록되었습니다", response));
    }

    // 모집글 전체 조회 (카테고리·악기·지역·기간·상태 필터 + 정렬 + 무한스크롤, 내가 차단한 유저의 글은 제외)
    // status(복수): 기본은 OPEN·PARTIALLY_CLOSED만, CLOSED를 고르면 마감된 글도 보인다(공연일이 지난 글은 계속 제외)
    // sort: LATEST(기본)·EVENT_SOON·CLOSING_SOON 화이트리스트 — 목록 밖의 값은 400. Pageable의 sort 파라미터와
    // 이름이 같지만 여기서는 항상 정렬을 덮어쓰므로(클라이언트의 ?sort=는 속성명으로 쓰이지 않는다) 충돌하지 않는다
    @GetMapping
    public ResponseEntity<APIResponse<PageResponse<PostSummaryResponseDTO>>> getPosts(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> instrument,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<PostStatus> status,
            @RequestParam(defaultValue = "LATEST") PostSort sort,
            @PageableDefault(size = 10) Pageable pageable) {

        // 정렬은 PostSpecification이 쿼리에 직접 건다 — Pageable은 정렬 없이 페이지 번호·크기만 쓴다
        Page<PostSummaryResponseDTO> response =
                postService.getPosts(user.getId(), category, instrument, region, startDate, endDate, status, sort,
                        PageSupport.withSort(pageable, Sort.unsorted()));
        return ResponseEntity.ok(APIResponse.ok("모집글 목록 조회 성공", PageResponse.from(response)));
    }

    /**
     * 내가 올린 모집글 목록 (마이페이지)
     * 변수 경로 /{id}보다 위에 선언해 라우팅 충돌 방지
     */
    @GetMapping("/me")
    public ResponseEntity<APIResponse<PageResponse<PostSummaryResponseDTO>>> getMyPosts(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<PostSummaryResponseDTO> response = postService.getMyPosts(user.getId(), PageSupport.withSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(APIResponse.ok("내 모집글 목록 조회 성공", PageResponse.from(response)));
    }

    // 모집글 단건 조회 (writer·applicationCount·isMine·hasApplied 포함)
    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<PostDetailResponseDTO>> getPost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        PostDetailResponseDTO response = postService.getPost(id, user);
        return ResponseEntity.ok(APIResponse.ok("모집글 조회 성공", response));
    }

    // 모집글 수정 (작성자만). 대기·수락 지원자에게 수정 알림 발송
    @RateLimit(limit = 20, windowSeconds = 600)  // 수정마다 지원자에게 푸시가 나갈 수 있다
    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<PostResponseDTO>> updatePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody @Valid PostCreateRequestDTO request) {

        PostResponseDTO response = postService.updatePost(id, user, request);
        return ResponseEntity.ok(APIResponse.ok("모집글이 수정되었습니다", response));
    }

    // 모집글 수동 전체 마감 (작성자만)
    @PatchMapping("/{id}/close")
    public ResponseEntity<APIResponse<Void>> closePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        postService.closePost(id, user);
        return ResponseEntity.ok(APIResponse.ok("모집글이 마감되었습니다"));
    }

    // 모집글 수동 마감 해제 = 모집 재개 (작성자만, 공연일이 지난 글은 400)
    @PatchMapping("/{id}/reopen")
    public ResponseEntity<APIResponse<Void>> reopenPost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        postService.reopenPost(id, user);
        return ResponseEntity.ok(APIResponse.ok("모집이 재개되었습니다"));
    }

    // 모집글 삭제 (작성자만). 연관 지원서도 함께 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<Void>> deletePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        postService.deletePost(id, user);
        return ResponseEntity.ok(APIResponse.ok("모집글이 삭제되었습니다"));
    }
}
