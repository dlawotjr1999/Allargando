package com.obri_back.obri.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/*
 * 내 정보 수정 요청 바디 (PUT /api/users/me)
 * null 필드는 미변경, careers는 전체 교체
 */
@Getter
@NoArgsConstructor
public class UserUpdateRequestDTO {

    // 형식(한글·영문·숫자·_ 2~20자)·예약어 검증은 NFC 정규화 뒤에 해야 하므로 DTO가 아니라 NicknamePolicy가 한다
    private String nickname;

    private String instrument;
    private List<CareerDTO> careers;  // id 있으면 수정, 없으면 추가
}