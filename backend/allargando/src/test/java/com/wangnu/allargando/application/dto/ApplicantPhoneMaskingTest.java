package com.wangnu.allargando.application.dto;

import com.wangnu.allargando.application.entity.Application;
import com.wangnu.allargando.application.entity.ApplicationStatus;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// D13 — 종료된 지원(거절·취소·철회)의 지원자 전화번호는 마스킹, 진행 중(PENDING)·확정(ACCEPTED)은 원문
class ApplicantPhoneMaskingTest {

    @Test
    void maskPhoneNumber_keepsFirstThreeAndLastFourCharacters() {
        assertThat(ApplicantResponseDTO.maskPhoneNumber("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(ApplicantResponseDTO.maskPhoneNumber("+821012345678")).isEqualTo("+82******5678");
        assertThat(ApplicantResponseDTO.maskPhoneNumber("01012345678")).isEqualTo("010****5678");
    }

    @Test
    void maskPhoneNumber_masksShortValuesAndKeepsNull() {
        assertThat(ApplicantResponseDTO.maskPhoneNumber("1234567")).isEqualTo("*******");
        assertThat(ApplicantResponseDTO.maskPhoneNumber(null)).isNull();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void appResponse_masksPhoneOnlyForEndedApplications(ApplicationStatus status) {
        User applicant = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).nickname("a").instrument("바이올린")
                .phoneNumber("010-1234-5678").build();
        User recruiter = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(2L).nickname("r").build();
        Post post = Post.create(recruiter, PostInfo.builder().category("앙상블").title("t")
                .eventAt(LocalDateTime.now().plusDays(3)).location("l").region("서울").timetable("t").build());
        Application application = Application.builder().user(applicant).post(post)
                .instrument("바이올린").status(status).build();

        AppResponseDTO response = AppResponseDTO.from(application, applicant, List.of());

        String expected = status == ApplicationStatus.PENDING || status == ApplicationStatus.ACCEPTED
                ? "010-1234-5678" : "010-****-5678";
        assertThat(response.getApplicant().getPhoneNumber()).isEqualTo(expected);
    }

    // 지원 악기(D9)는 프로필 악기와 달라도 지원서에 저장된 값이 응답의 instrument로 나간다
    @Test
    void appResponse_exposesAppliedInstrumentSeparatelyFromProfileInstrument() {
        User applicant = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).nickname("a").instrument("바이올린")
                .phoneNumber("010-1234-5678").build();
        User recruiter = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(2L).nickname("r").build();
        Post post = Post.create(recruiter, PostInfo.builder().category("앙상블").title("t")
                .eventAt(LocalDateTime.now().plusDays(3)).location("l").region("서울").timetable("t").build());
        Application application = Application.builder().user(applicant).post(post)
                .instrument("비올라").status(ApplicationStatus.PENDING).build();

        AppResponseDTO response = AppResponseDTO.from(application, applicant, List.of());

        assertThat(response.getInstrument()).isEqualTo("비올라");
        assertThat(response.getApplicant().getInstrument()).isEqualTo("바이올린");
    }
}
