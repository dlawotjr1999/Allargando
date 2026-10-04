package com.wangnu.allargando.application.service;

import com.wangnu.allargando.application.entity.Application;
import com.wangnu.allargando.application.entity.ApplicationStatus;
import com.wangnu.allargando.global.exception.ForbiddenException;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// CLAUDE.md §8: ApplicationService에 흩어져 있던 3곳의 인가 체크(requireRecruiter 헬퍼·cancel()·getApplication()
// 인라인)를 Policy로 통합 — 판단 로직 자체는 Post·Application 엔티티에 위임하고, 이 클래스는 그 결과를 물어보기만 함
class ApplicationAccessPolicyTest {

    private final ApplicationAccessPolicy accessPolicy = new ApplicationAccessPolicy();

    private final User applicant = User.builder().id(1L).nickname("applicant").firebaseUid("applicant-uid").build();
    private final User recruiter = User.builder().id(2L).nickname("recruiter").firebaseUid("recruiter-uid").build();
    private final User stranger = User.builder().id(3L).nickname("stranger").firebaseUid("stranger-uid").build();

    private Application buildApplication() {
        Post post = Post.create(recruiter, PostInfo.builder().build());
        return Application.builder().id(100L).user(applicant).post(post).instrument("바이올린")
                .status(ApplicationStatus.PENDING).build();
    }

    @Test
    void requireRecruiterOnPost_passesWhenOwner() {
        Post post = Post.create(recruiter, PostInfo.builder().build());

        assertThatCode(() -> accessPolicy.requireRecruiter(recruiter, post, "모집자만 가능합니다"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireRecruiterOnPost_throwsForbiddenWhenNotOwner() {
        Post post = Post.create(recruiter, PostInfo.builder().build());

        assertThatThrownBy(() -> accessPolicy.requireRecruiter(applicant, post, "모집자만 가능합니다"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("모집자만 가능합니다");
    }

    @Test
    void requireRecruiterOnApplication_passesWhenPostOwner() {
        Application application = buildApplication();

        assertThatCode(() -> accessPolicy.requireRecruiter(recruiter, application, "모집자만 가능합니다"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireRecruiterOnApplication_throwsForbiddenWhenNotPostOwner() {
        Application application = buildApplication();

        assertThatThrownBy(() -> accessPolicy.requireRecruiter(applicant, application, "모집자만 가능합니다"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("모집자만 가능합니다");
    }

    @Test
    void requireApplicant_passesWhenApplicant() {
        Application application = buildApplication();

        assertThatCode(() -> accessPolicy.requireApplicant(applicant, application, "지원자만 가능합니다"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireApplicant_throwsForbiddenWhenNotApplicant() {
        Application application = buildApplication();

        assertThatThrownBy(() -> accessPolicy.requireApplicant(recruiter, application, "지원자만 가능합니다"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("지원자만 가능합니다");
    }

    @Test
    void requireViewer_passesWhenApplicant() {
        Application application = buildApplication();

        assertThatCode(() -> accessPolicy.requireViewer(applicant, application, "조회 권한이 없습니다"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireViewer_passesWhenRecruiter() {
        Application application = buildApplication();

        assertThatCode(() -> accessPolicy.requireViewer(recruiter, application, "조회 권한이 없습니다"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireViewer_throwsForbiddenWhenNeither() {
        Application application = buildApplication();

        assertThatThrownBy(() -> accessPolicy.requireViewer(stranger, application, "조회 권한이 없습니다"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("조회 권한이 없습니다");
    }
}
