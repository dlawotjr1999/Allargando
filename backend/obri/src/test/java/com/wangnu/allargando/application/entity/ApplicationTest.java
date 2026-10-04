package com.wangnu.allargando.application.entity;

import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// CLAUDE.md §8: ApplicationAccessPolicy가 getUser().getId().equals(...) 체인을 직접 다루지 않도록
// 지원자/모집자 판단 로직 자체를 Application으로 이관(Tell-Don't-Ask)
class ApplicationTest {

    private final User applicant = User.builder().id(1L).nickname("applicant").firebaseUid("applicant-uid").build();
    private final User recruiter = User.builder().id(2L).nickname("recruiter").firebaseUid("recruiter-uid").build();

    private Application buildApplication() {
        Post post = Post.create(recruiter, PostInfo.builder().build());
        return Application.builder().id(100L).user(applicant).post(post).instrument("바이올린")
                .status(ApplicationStatus.PENDING).build();
    }

    @Test
    void isApplicant_trueWhenSameUser() {
        Application application = buildApplication();

        assertThat(application.isApplicant(applicant)).isTrue();
    }

    @Test
    void isApplicant_falseWhenDifferentUser() {
        Application application = buildApplication();

        assertThat(application.isApplicant(recruiter)).isFalse();
    }

    @Test
    void isRecruiter_trueWhenPostOwner() {
        Application application = buildApplication();

        assertThat(application.isRecruiter(recruiter)).isTrue();
    }

    @Test
    void isRecruiter_falseWhenNotPostOwner() {
        Application application = buildApplication();

        assertThat(application.isRecruiter(applicant)).isFalse();
    }
}
