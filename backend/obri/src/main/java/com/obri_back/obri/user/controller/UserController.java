package com.obri_back.obri.user.controller;

import com.obri_back.obri.global.common.APIResponse;
import com.obri_back.obri.user.dto.UserPublicProfileDTO;
import com.obri_back.obri.user.dto.UserResponseDTO;
import com.obri_back.obri.user.dto.UserUpdateRequestDTO;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.service.UserService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 유저 관련 API 컨트롤러
 * GET    /api/users/me                — 내 정보 조회
 * PUT    /api/users/me                — 내 정보 수정
 * DELETE /api/users/me                — 회원 탈퇴
 * GET    /api/users/check/{nickname}  — 닉네임 중복 체크
 * GET    /api/users/{nickname}        — 타인 프로필 조회
 * (내 모집글/지원 목록은 GET /api/posts/me · GET /api/applications/me 로 각 도메인이 소유)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

      /**
     * 내 정보 조회
     * SecurityContext에서 현재 로그인한 유저를 꺼내 정보 반환
     */
    @GetMapping("/me")
    public ResponseEntity<APIResponse<UserResponseDTO>> getMyInfo(
            @AuthenticationPrincipal User user) {

        UserResponseDTO response = userService.getMyInfo(user.getId());
        return ResponseEntity.ok(APIResponse.ok("내 정보 조회 성공", response));
    }

    /**
     * 내 정보 수정
     * 모든 필드가 유효한 경우에만 수정 가능 (PUT)
     */
    @PutMapping("/me")
    public ResponseEntity<APIResponse<UserResponseDTO>> updateMyInfo(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid UserUpdateRequestDTO request) {

        UserResponseDTO response = userService.updateMyInfo(user, request);
        return ResponseEntity.ok(APIResponse.ok("내 정보가 수정되었습니다.", response));
    }

    /**
     * 회원 탈퇴
     */
    @DeleteMapping("/me")
    public ResponseEntity<APIResponse<Void>> deleteUser(
            @AuthenticationPrincipal User user) {

        userService.deleteUser(user);
        return ResponseEntity.ok(APIResponse.ok("회원 탈퇴가 완료되었습니다."));
    }

    /**
     * 닉네임 중복 체크
     * 인증이 필요 없는 경로 (SecurityConfig에서 permitAll 설정)
     */
    @GetMapping("/check/{nickname}")
    public ResponseEntity<APIResponse<Map<String, Boolean>>> checkNickname(
            @PathVariable String nickname) {

        boolean isDuplicated = userService.checkNickname(nickname);
        return ResponseEntity.ok(APIResponse.ok("닉네임 중복 체크 성공",
                Map.of("isDuplicated", isDuplicated)));
    }

    /**
     * 유저 프로필 조회
     * 리터럴 경로(/me, /check/{nickname})가 변수 경로(/{nickname})보다 먼저 매칭되므로 충돌하지 않는다
     * (닉네임 me는 NicknamePolicy가 예약어로 막는다)
     */
    @GetMapping("/{nickname}")
    public ResponseEntity<APIResponse<UserPublicProfileDTO>> getUserProfile(
            @PathVariable String nickname) {

        UserPublicProfileDTO response = userService.getUserProfile(nickname);
        return ResponseEntity.ok(APIResponse.ok("유저 프로필 조회 성공", response));
    }
}
