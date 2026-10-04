package com.wangnu.allargando.post.service;

import com.wangnu.allargando.application.entity.Application;
import com.wangnu.allargando.application.entity.ApplicationStatus;
import com.wangnu.allargando.application.service.ApplicationAccessPolicy;
import com.wangnu.allargando.application.service.ApplicationService;
import com.wangnu.allargando.block.service.BlockService;
import com.wangnu.allargando.global.exception.BadRequestException;
import com.wangnu.allargando.post.dto.PostCreateRequestDTO;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 글 수정으로 악기를 삭제할 때의 규칙(D12)을 실제 서비스·H2로 검증한다 — 수락자가 있는 악기는 400,
// 수락자가 없는 악기는 삭제되고 그 악기의 대기 지원만 자동 거절된다(쿼리·변경 감지 저장은 Mockito로 확인 불가).
// 설정 프로퍼티의 의도는 PostSpecificationTest 주석 참고(H2 예약어·Flyway·ddl-auto 우회)
@DataJpaTest
@Import({PostService.class, ApplicationService.class, ApplicationAccessPolicy.class, UserService.class, BlockService.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PostInstrumentRemovalFlowTest {

    @Autowired TestEntityManager em;
    @Autowired PostService postService;

    private User newUser(String name) {
        return em.persist(User.builder().firebaseUid(name + "-uid").phoneNumber("010-" + name)
                .nickname(name).instrument("바이올린").build());
    }

    private Post newPost(User owner) {
        Post post = Post.create(owner, PostInfo.builder().category("앙상블").title("모집")
                .eventAt(LocalDateTime.now().plusDays(7)).location("서울").region("서울").timetable("13:00").build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 2));
        post.addInstrument(PostInstrument.of(post, "첼로", 2));
        return em.persist(post);
    }

    private Application apply(User user, Post post, String instrument, ApplicationStatus status) {
        return em.persist(Application.builder().user(user).post(post).instrument(instrument).status(status).build());
    }

    private PostCreateRequestDTO onlyViolin() {
        return PostCreateRequestDTO.builder().category("앙상블").title("수정")
                .eventAt(LocalDateTime.now().plusDays(7)).location("서울").region("서울").timetable("13:00")
                .instruments(List.of(PostCreateRequestDTO.InstrumentItem.builder().instrument("바이올린").people(2).build()))
                .build();
    }

    private ApplicationStatus statusOf(Application application) {
        em.flush();
        em.clear();
        return em.find(Application.class, application.getId()).getStatus();
    }

    // 수락자가 없는 첼로를 빼면 첼로의 대기 지원만 REJECTED, 바이올린 지원과 이미 끝난 지원은 그대로
    @Test
    void updatePost_removingInstrumentWithoutAcceptedApplicants_rejectsOnlyItsPendingApplications() {
        User owner = newUser("owner");
        Post post = newPost(owner);
        Application cellistPending = apply(newUser("c1"), post, "첼로", ApplicationStatus.PENDING);
        Application cellistCancelled = apply(newUser("c2"), post, "첼로", ApplicationStatus.CANCELLED);
        Application violinistPending = apply(newUser("v1"), post, "바이올린", ApplicationStatus.PENDING);

        postService.updatePost(post.getId(), owner, onlyViolin());

        assertThat(statusOf(cellistPending)).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(statusOf(cellistCancelled)).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(statusOf(violinistPending)).isEqualTo(ApplicationStatus.PENDING);
    }

    // 수락자가 있는 첼로를 빼려 하면 400이고 대기 지원도 건드리지 않는다
    @Test
    void updatePost_removingInstrumentWithAcceptedApplicants_isRejectedAndChangesNothing() {
        User owner = newUser("owner");
        Post post = newPost(owner);
        post.confirmInstrument("첼로");
        apply(newUser("c1"), post, "첼로", ApplicationStatus.ACCEPTED);
        Application cellistPending = apply(newUser("c2"), post, "첼로", ApplicationStatus.PENDING);

        assertThatThrownBy(() -> postService.updatePost(post.getId(), owner, onlyViolin()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("먼저 철회해 주세요");

        assertThat(statusOf(cellistPending)).isEqualTo(ApplicationStatus.PENDING);
    }
}
