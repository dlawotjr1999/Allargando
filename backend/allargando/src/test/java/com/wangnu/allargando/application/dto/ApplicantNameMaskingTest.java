package com.wangnu.allargando.application.dto;

import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 지원자 이름은 전화번호와 같은 규칙으로 노출된다: 진행 중인 지원은 원문, 종료된 지원은 가린다
class ApplicantNameMaskingTest {

    private User user() {
        return User.builder().name("홍길동").termsAgreedAt(LocalDateTime.now())
                .nickname("tester").instrument("바이올린").phoneNumber("010-1234-5678").build();
    }

    @Test
    void from_exposesNameWhenNotMasked() {
        ApplicantResponseDTO dto = ApplicantResponseDTO.from(user(), List.of(), false);

        assertThat(dto.getName()).isEqualTo("홍길동");
    }

    @Test
    void from_masksNameWhenPhoneIsMasked() {
        ApplicantResponseDTO dto = ApplicantResponseDTO.from(user(), List.of(), true);

        assertThat(dto.getName()).isEqualTo("홍**");
    }

    @Test
    void maskName_handlesNullAndShortNames() {
        assertThat(ApplicantResponseDTO.maskName(null)).isNull();
        assertThat(ApplicantResponseDTO.maskName("김")).isEqualTo("김");
        assertThat(ApplicantResponseDTO.maskName("김철")).isEqualTo("김*");
    }
}
