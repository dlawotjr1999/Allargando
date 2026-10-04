package com.wangnu.allargando.application.controller;

import com.wangnu.allargando.application.dto.AppRequestDTO;
import com.wangnu.allargando.application.dto.AppResponseDTO;
import com.wangnu.allargando.application.service.ApplicationService;
import com.wangnu.allargando.global.common.APIResponse;
import com.wangnu.allargando.global.common.PageSupport;
import com.wangnu.allargando.global.common.PageResponse;
import com.wangnu.allargando.user.entity.User;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/*
 * 지원(Application) 관련 API 컨트롤러
 * POST  /api/applications/submit                        — 지원서 제출
 * GET   /api/applications/me                            — 내 지원 목록 (마이페이지)
 * GET   /api/applications/{id}                          — 지원서 단건 조회
 * PATCH /api/applications/{id}/{accept|reject|cancel|revoke} — 상태 변경 (의도별 분리)
 * GET   /api/applications/post/{postId}                 — 모집글별 지원자 목록 (모집자용)
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService applicationService;

    // 지원서 제출
    @PostMapping("/submit")
    public ResponseEntity<APIResponse<AppResponseDTO>> submitApplication(
        @AuthenticationPrincipal User user,
        @RequestBody @Valid AppRequestDTO requestDto
    ) {
        AppResponseDTO response = applicationService.submitApplication(user, requestDto);
        return ResponseEntity.ok(APIResponse.ok("지원서가 제출되었습니다", response));
    }

    // 내 지원 목록 (마이페이지) — 변수 경로 /{id}보다 위에 선언해 라우팅 충돌 방지
    @GetMapping("/me")
    public ResponseEntity<APIResponse<PageResponse<AppResponseDTO>>> getMyApplications(
        @AuthenticationPrincipal User user,
        @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<AppResponseDTO> response = applicationService.getApplicationsByUserId(
                user.getId(), PageSupport.withSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(APIResponse.ok("내 지원 목록 조회 성공", PageResponse.from(response)));
    }

    // 지원서 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<AppResponseDTO>> getApplication(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        AppResponseDTO response = applicationService.getApplication(user, id);
        return ResponseEntity.ok(APIResponse.ok("지원서를 조회했습니다", response));
    }

    // 지원 수락 (모집자, PENDING → ACCEPTED)
    @PatchMapping("/{id}/accept")
    public ResponseEntity<APIResponse<Void>> acceptApplication(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        applicationService.accept(user, id);
        return ResponseEntity.ok(APIResponse.ok("지원을 수락했습니다"));
    }

    // 지원 거절 (모집자, PENDING → REJECTED)
    @PatchMapping("/{id}/reject")
    public ResponseEntity<APIResponse<Void>> rejectApplication(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        applicationService.reject(user, id);
        return ResponseEntity.ok(APIResponse.ok("지원을 거절했습니다"));
    }

    // 지원 취소 (지원자, PENDING → CANCELLED)
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<APIResponse<Void>> cancelApplication(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        applicationService.cancel(user, id);
        return ResponseEntity.ok(APIResponse.ok("지원을 취소했습니다"));
    }

    // 수락 철회 (모집자, ACCEPTED → REVOKED; 확정 취소·자리 재오픈)
    @PatchMapping("/{id}/revoke")
    public ResponseEntity<APIResponse<Void>> revokeApplication(
        @AuthenticationPrincipal User user,
        @PathVariable Long id
    ) {
        applicationService.revoke(user, id);
        return ResponseEntity.ok(APIResponse.ok("수락을 철회했습니다"));
    }

    // 모집글별 지원자 목록 (모집자용)
    // PostController 이동 시 PostController → ApplicationService cross-domain 의존이 생기므로 여기에 유지
    @GetMapping("/post/{postId}")
    public ResponseEntity<APIResponse<PageResponse<AppResponseDTO>>> getApplicationsByPostId(
        @AuthenticationPrincipal User user,
        @PathVariable Long postId,
        @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<AppResponseDTO> response = applicationService.getApplicationsByPostId(
                postId, user, PageSupport.withSort(pageable, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(APIResponse.ok("지원자 목록 조회 성공", PageResponse.from(response)));
    }
}
