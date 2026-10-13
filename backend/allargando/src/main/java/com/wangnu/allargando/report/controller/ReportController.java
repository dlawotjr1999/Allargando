package com.wangnu.allargando.report.controller;

import com.wangnu.allargando.global.ratelimit.RateLimit;
import com.wangnu.allargando.global.common.APIResponse;
import com.wangnu.allargando.report.dto.ReportRequestDTO;
import com.wangnu.allargando.report.service.ReportService;
import com.wangnu.allargando.user.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 API 컨트롤러
 * POST /api/reports/posts/{postId}      — 모집글 신고
 * POST /api/reports/users/{nickname}    — 유저 신고
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // 모집글 신고
    @RateLimit(limit = 20, windowSeconds = 3600)
    @PostMapping("/posts/{postId}")
    public ResponseEntity<APIResponse<Void>> reportPost(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId,
            @RequestBody @Valid ReportRequestDTO request) {
        reportService.reportPost(user, postId, request);
        return ResponseEntity.ok(APIResponse.ok("신고가 접수되었습니다"));
    }

    // 유저 신고
    @RateLimit(limit = 20, windowSeconds = 3600)
    @PostMapping("/users/{nickname}")
    public ResponseEntity<APIResponse<Void>> reportUser(
            @AuthenticationPrincipal User user,
            @PathVariable String nickname,
            @RequestBody @Valid ReportRequestDTO request) {
        reportService.reportUser(user, nickname, request);
        return ResponseEntity.ok(APIResponse.ok("신고가 접수되었습니다"));
    }
}
