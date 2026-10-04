package com.wangnu.allargando.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/*
 * 내 정보 수정 요청 바디 (PUT /api/users/me)
 * 닉네임·악기는 가입과 같이 필수(PUT 전체 교체), careers는 보내면 전체 교체하고 null이면 경력만 미변경
 */
@Getter
@NoArgsConstructor
public class UserUpdateRequestDTO {

    // 형식(한글·영문·숫자·_ 2~20자)·예약어 검증은 NFC 정규화 뒤에 해야 하므로 DTO가 아니라 NicknamePolicy가 한다
    @NotBlank(message = "닉네임을 입력해주세요")
    private String nickname;

    @NotBlank(message = "악기를 입력해주세요")
    private String instrument;

    // 전체 교체: 요청 목록으로 기존 경력을 모두 대체한다(요청의 id는 쓰이지 않음). null이면 경력을 건드리지 않는다
    @Valid
    @Size(max = 10, message = "경력은 최대 10개까지 등록할 수 있습니다")
    private List<CareerDTO> careers;
}