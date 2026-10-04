package com.wangnu.allargando.block.dto;

import com.wangnu.allargando.block.entity.UserBlock;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/*
 * 차단 목록 응답 DTO — 차단 관리 화면용. 연락처 등은 노출하지 않고 식별에 필요한 최소 정보만
 */
@Getter
@Builder
public class BlockedUserResponseDTO {
    private String nickname;
    private String instrument;
    private LocalDateTime blockedAt;

    // UserBlock 엔티티 → 응답 DTO 변환
    public static BlockedUserResponseDTO from(UserBlock block) {
        return BlockedUserResponseDTO.builder()
                .nickname(block.getBlocked().getNickname())
                .instrument(block.getBlocked().getInstrument())
                .blockedAt(block.getCreatedAt())
                .build();
    }
}
