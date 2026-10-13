package com.wangnu.allargando.block.controller;

import com.wangnu.allargando.global.ratelimit.RateLimit;
import com.wangnu.allargando.block.dto.BlockRequestDTO;
import com.wangnu.allargando.block.dto.BlockedUserResponseDTO;
import com.wangnu.allargando.block.service.BlockService;
import com.wangnu.allargando.global.common.APIResponse;
import com.wangnu.allargando.user.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 유저 차단 API 컨트롤러
 * POST   /api/blocks             — 유저 차단 (닉네임 지정)
 * GET    /api/blocks             — 내 차단 목록
 * DELETE /api/blocks/{nickname}  — 차단 해제
 */
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    // 유저 차단
    @RateLimit(limit = 30, windowSeconds = 3600)
    @PostMapping
    public ResponseEntity<APIResponse<Void>> block(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid BlockRequestDTO request) {
        blockService.block(user, request.getNickname());
        return ResponseEntity.ok(APIResponse.ok("사용자를 차단했습니다"));
    }

    // 내 차단 목록
    @GetMapping
    public ResponseEntity<APIResponse<List<BlockedUserResponseDTO>>> getMyBlocks(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(APIResponse.ok("차단 목록 조회 성공", blockService.getMyBlocks(user.getId())));
    }

    // 차단 해제
    @DeleteMapping("/{nickname}")
    public ResponseEntity<APIResponse<Void>> unblock(
            @AuthenticationPrincipal User user,
            @PathVariable String nickname) {
        blockService.unblock(user, nickname);
        return ResponseEntity.ok(APIResponse.ok("차단을 해제했습니다"));
    }
}
