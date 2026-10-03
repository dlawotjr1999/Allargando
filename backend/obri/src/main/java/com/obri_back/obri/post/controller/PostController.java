package com.obri_back.obri.post.controller;

import com.obri_back.obri.global.common.APIResponse;
import com.obri_back.obri.global.common.PageResponse;
import com.obri_back.obri.post.dto.PostCreateRequestDTO;
import com.obri_back.obri.post.dto.PostDetailResponseDTO;
import com.obri_back.obri.post.dto.PostResponseDTO;
import com.obri_back.obri.post.dto.PostSummaryResponseDTO;
import com.obri_back.obri.post.service.PostService;
import com.obri_back.obri.user.entity.User;
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
 * GET    /api/posts             — 모집글 전체 조회 (필터·페이지네이션)
 * GET    /api/posts/{id}        — 모집글 단건 조회
 * PUT    /api/posts/{id}        — 모집글 수정 (작성자만)
 * PATCH  /api/posts/{id}/close  — 모집글 수동 전체 마감 (작성자만)
 * DELETE /api/posts/{id}        — 모집글 삭제 (작성자만)
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 모집글 등록 (등록 성공 시 전체 broadcast 알림)
    @PostMapping
    public ResponseEntity<APIResponse<PostResponseDTO>> createPost(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid PostCreateRequestDTO request) {
        PostResponseDTO response = postService.createPost(user, request);
        return ResponseEntity.ok(APIResponse.ok("모집글이 등록되었습니다", response));
    }

    // 모집글 전체 조회 (카테고리·악기·지역·기간 필터 + 무한스크롤, 내가 차단한 유저의 글은 제외)
    // status 필터 파라미터 없음(BACKLOG.md #35) — 공개 목록은 항상 OPEN·PARTIALLY_CLOSED만 노출,
    // CLOSED(마감)된 글은 이 엔드포인트로 조회 불가. 작성자 본인의 마감글은 GET /api/posts/me로 조회
    @GetMapping
    public ResponseEntity<APIResponse<PageResponse<PostSummaryResponseDTO>>> getPosts(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> instrument,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<PostSummaryResponseDTO> response =
                postService.getPosts(user.getId(), category, instrument, region, startDate, endDate, pageable);
        return ResponseEntity.ok(APIResponse.ok("모집글 목록 조회 성공", PageResponse.from(response)));
    }

    /**
     * 내가 올린 모집글 목록 (마이페이지)
     * 변수 경로 /{id}보다 위에 선언해 라우팅 충돌 방지
     */
    @GetMapping("/me")
    public ResponseEntity<APIResponse<PageResponse<PostSummaryResponseDTO>>> getMyPosts(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<PostSummaryResponseDTO> response = postService.getMyPosts(user.getId(), pageable);
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

    // 모집글 삭제 (작성자만). 연관 지원서도 함께 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<Void>> deletePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        postService.deletePost(id, user);
        return ResponseEntity.ok(APIResponse.ok("모집글이 삭제되었습니다"));
    }
}
