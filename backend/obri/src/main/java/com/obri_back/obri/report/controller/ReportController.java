package com.obri_back.obri.report.controller;

import com.obri_back.obri.global.common.APIResponse;
import com.obri_back.obri.report.dto.ReportRequestDTO;
import com.obri_back.obri.report.service.ReportService;
import com.obri_back.obri.user.entity.User;

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
    @PostMapping("/posts/{postId}")
    public ResponseEntity<APIResponse<Void>> reportPost(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId,
            @RequestBody @Valid ReportRequestDTO request) {
        reportService.reportPost(user, postId, request);
        return ResponseEntity.ok(APIResponse.ok("신고가 접수되었습니다"));
    }

    // 유저 신고
    @PostMapping("/users/{nickname}")
    public ResponseEntity<APIResponse<Void>> reportUser(
            @AuthenticationPrincipal User user,
            @PathVariable String nickname,
            @RequestBody @Valid ReportRequestDTO request) {
        reportService.reportUser(user, nickname, request);
        return ResponseEntity.ok(APIResponse.ok("신고가 접수되었습니다"));
    }
}
