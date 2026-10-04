package com.wangnu.allargando.auth.dto;

import com.wangnu.allargando.user.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
 * 회원가입 응답 바디 — 명세상 가입 시각(createdAt)만 반환
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDTO {
    private LocalDateTime createdAt;

    // User 엔티티에서 응답 DTO로 변환
    public static RegisterResponseDTO from(User user) {
        return RegisterResponseDTO.builder()
                .createdAt(user.getCreatedAt())
                .build();
    }
}
