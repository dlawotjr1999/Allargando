package com.obri_back.obri.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/*
 * 지원서 제출 요청 바디 (POST /api/applications/submit)
 * 지원자 프로필(악기·약력)은 토큰으로 조회하고 여기서는 대상 글·어필 문구만 받음
 */
@Getter
@Builder
public class AppRequestDTO {
    // 누락 시 findById(null) → 500이 나던 것을 사전 400으로 차단
    @NotNull(message = "모집글 ID를 입력해주세요")
    private Long postId;

    // 지원자가 고른 악기(D9). 보내지 않으면 프로필 악기로 대신한다 — 지원 모달이 악기 선택을 보내기 전까지의 호환용이며,
    // 프론트가 항상 보내게 되면 @NotBlank로 바꾼다
    @Size(max = 255, message = "악기는 255자 이내여야 합니다")
    private String instrument;

    @Size(max = 255, message = "어필 문구는 255자 이내여야 합니다")
    private String additionalInfo;

    // 필드로부터 요청 DTO 생성 (주로 테스트 편의용)
    public static AppRequestDTO from(Long postId, String additionalInfo) {
        return AppRequestDTO.builder()
                .postId(postId)
                .additionalInfo(additionalInfo)
                .build();
    }
}
