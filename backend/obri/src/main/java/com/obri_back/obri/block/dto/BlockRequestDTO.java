package com.obri_back.obri.block.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 유저 차단 요청 바디 (POST /api/blocks) — 앱 화면에는 닉네임만 보이므로 닉네임(UNIQUE)으로 대상을 지정
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockRequestDTO {

    @NotBlank(message = "차단할 닉네임을 입력해주세요")
    private String nickname;
}
