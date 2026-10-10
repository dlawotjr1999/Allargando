package com.wangnu.allargando.auth.dto;

import com.wangnu.allargando.user.dto.CareerDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/*
 * 회원가입 요청 바디 (POST /api/auth/register)
 * Firebase 인증(UID·이메일)은 토큰에서 취하고, 여기서는 추가 프로필만 받음
 * 필수 필드 검증 필요 — 누락 시 DB NOT NULL 위반으로 500이 나면서 Firebase 계정 보상 삭제까지 발동하므로
 * save() 이전(400)에 반드시 차단해야 함
 */
@Getter
@NoArgsConstructor
public class RegisterRequestDTO {
    // 형식(한글·영문·숫자·_ 2~20자)·예약어 검증은 NFC 정규화 뒤에 해야 하므로 DTO가 아니라 NicknamePolicy가 한다
    @NotBlank(message = "이름을 입력해주세요")
    private String name;

    @NotBlank(message = "닉네임을 입력해주세요")
    private String nickname;

    @NotBlank(message = "악기를 입력해주세요")
    private String instrument;

    @Valid
    @Size(max = 10, message = "경력은 최대 10개까지 등록할 수 있습니다")
    private List<CareerDTO> careers;

    // 동의 여부 — 둘 다 true여야 가입된다. 동의 시각은 서버가 기록하므로 요청으로 받지 않는다
    @NotNull(message = "이용약관 동의 여부가 필요합니다")
    @AssertTrue(message = "이용약관에 동의해주세요")
    private Boolean agreedToTerms;

    @NotNull(message = "개인정보 수집 동의 여부가 필요합니다")
    @AssertTrue(message = "개인정보 수집·이용에 동의해주세요")
    private Boolean agreedToPrivacy;
}
