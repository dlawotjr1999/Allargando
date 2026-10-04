package com.wangnu.allargando.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * FCM 토큰 갱신 요청 바디 (PATCH /api/auth/fcm-token)
 */
@Getter
@NoArgsConstructor
public class FCMTokenUpdateRequestDTO {
    @NotBlank(message = "FCM 토큰을 입력해주세요")
    private String fcmToken;
}