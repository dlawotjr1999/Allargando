package com.wangnu.allargando.auth.controller;

import com.wangnu.allargando.auth.dto.FCMTokenUpdateRequestDTO;
import com.wangnu.allargando.auth.dto.RegisterRequestDTO;
import com.wangnu.allargando.auth.dto.RegisterResponseDTO;
import com.wangnu.allargando.auth.service.AuthService;
import com.wangnu.allargando.global.common.APIResponse;
import com.wangnu.allargando.global.exception.UnauthorizedException;
import com.wangnu.allargando.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 관련 API 컨트롤러
 * Firebase Authentication과 연동해 회원가입 및 FCM 토큰 관리
 * POST  /api/auth/register      — 회원가입
 * PATCH  /api/auth/fcm-token    — FCM 토큰 갱신
 * DELETE /api/auth/fcm-token    — FCM 토큰 해제 (로그아웃·알림 끄기)
 * PATCH /api/auth/phone-number  — 전화번호 갱신
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /*
     * 회원가입
     * Firebase 가입 후 DB에 유저 정보 저장
     * Authorization 헤더의 Firebase ID Token에서 UID와 이메일 추출
     *
     * @param authorization Firebase ID Token (Bearer {token})
     * @param request       닉네임, 악기, 경력 등 추가 정보
     * @return 가입 시각(createdAt)만 포함한 응답
     */
    @PostMapping("/register")
    public ResponseEntity<APIResponse<RegisterResponseDTO>> register(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody @Valid RegisterRequestDTO request) {

        String idToken = extractBearerToken(authorization);
        RegisterResponseDTO response = authService.register(idToken, request);

        return ResponseEntity.ok(APIResponse.ok("회원가입이 완료되었습니다", response));
    }

    /*
     * FCM 토큰 갱신
     * 앱 실행 시 최신 FCM 토큰을 서버에 저장
     * 푸시 알림 발송 시 이 토큰을 사용
     *
     * @param user    현재 로그인한 유저 (SecurityContext에서 추출)
     * @param request 새 FCM 토큰
     * @return 성공 메시지
     */
    @PatchMapping("/fcm-token")
    public ResponseEntity<APIResponse<Void>> updateFcmToken(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid FCMTokenUpdateRequestDTO request) {

        authService.updateFcmToken(user, request);

        return ResponseEntity.ok(APIResponse.ok("FCM 토큰이 갱신되었습니다"));
    }

    // FCM 토큰 해제 — 앱이 로그아웃·탈퇴·알림 끄기 직전에 호출한다
    @DeleteMapping("/fcm-token")
    public ResponseEntity<APIResponse<Void>> clearFcmToken(@AuthenticationPrincipal User user) {
        authService.clearFcmToken(user);

        return ResponseEntity.ok(APIResponse.ok("FCM 토큰이 해제되었습니다"));
    }

    /*
     * 전화번호 갱신
     * register()와 동일하게 Authorization 헤더의 Firebase ID Token만으로 처리 (요청 바디 없음)
     *
     * @param authorization Firebase ID Token (Bearer {token})
     * @param user          현재 로그인한 유저 (SecurityContext에서 추출)
     * @return 성공 메시지
     */
    @PatchMapping("/phone-number")
    public ResponseEntity<APIResponse<Void>> updatePhoneNumber(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @AuthenticationPrincipal User user) {

        String idToken = extractBearerToken(authorization);
        authService.updatePhoneNumber(user, idToken);

        return ResponseEntity.ok(APIResponse.ok("전화번호가 변경되었습니다"));
    }

    /*
     * Authorization 헤더에서 Bearer 토큰만 추출
     * 헤더 누락(required=false로 받아 null)·형식 오류·빈 토큰을 모두 401로 통일한다(누락만 400이던 불일치 해소, D18)
     * 형식을 먼저 검증하는 이유: 무조건 substring(7)을 하면 헤더가 7자 미만일 때
     * StringIndexOutOfBoundsException이 나고, register는 permitAll이라 미인증 상태로도 500을 유발할 수 있다
     *
     * param : authorization Authorization 헤더 원문
     * return : "Bearer " 를 제거한 ID Token
     */
    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authorization 헤더 형식이 올바르지 않습니다");
        }

        String idToken = authorization.substring(7);
        if (idToken.isBlank()) {
            throw new UnauthorizedException("토큰이 비어 있습니다");
        }
        return idToken;
    }
}