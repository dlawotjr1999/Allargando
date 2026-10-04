package com.wangnu.allargando.application.service;

import com.wangnu.allargando.application.entity.Application;
import com.wangnu.allargando.application.entity.ApplicationStatus;
import com.wangnu.allargando.block.service.BlockService;
import com.wangnu.allargando.notification.event.ApplicationResultNotificationEvent;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 정원 마감 시 같은 악기의 대기 지원이 자동 거절되는지 실제 서비스·리포지토리(H2)로 검증한다(D10, APP-T18).
// 쿼리(악기·상태 조건)와 변경 감지 저장은 Mockito 단위 테스트로 확인할 수 없어 DB까지 엮는다.
// 설정 프로퍼티의 의도는 PostSpecificationTest 주석 참고(H2 예약어·Flyway·ddl-auto 우회)
@DataJpaTest
@RecordApplicationEvents
@Import({ApplicationService.class, ApplicationAccessPolicy.class, UserService.class, BlockService.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApplicationAutoRejectFlowTest {

    @Autowired TestEntityManager em;
    @Autowired ApplicationService applicationService;
    @Autowired ApplicationEvents events;

    private User newUser(String name) {
        return em.persist(User.builder().firebaseUid(name + "-uid").phoneNumber("010-" + name)
                .nickname(name).instrument("바이올린").build());
    }

    private Post newPost(User owner, int violinPeople) {
        Post post = Post.create(owner, PostInfo.builder().category("앙상블").title("모집").eventAt(LocalDateTime.now().plusDays(7))
                .location("서울").region("서울").timetable("13:00").build());
        post.addInstrument(PostInstrument.of(post, "바이올린", violinPeople));
        post.addInstrument(PostInstrument.of(post, "첼로", 2));
        return em.persist(post);
    }

    private Application apply(User user, Post post, String instrument) {
        return em.persist(Application.builder().user(user).post(post).instrument(instrument)
                .status(ApplicationStatus.PENDING).build());
    }

    private ApplicationStatus statusOf(Application application) {
        em.flush();
        em.clear();
        return em.find(Application.class, application.getId()).getStatus();
    }

    // 마지막 자리를 수락하면 같은 악기의 대기 지원은 REJECTED, 다른 악기·이미 처리된 지원은 그대로, 거절마다 알림 이벤트
    @Test
    void accept_rejectsOtherPendingApplicationsOfSameInstrumentWhenItFillsTheInstrument() {
        User recruiter = newUser("recruiter");
        Post post = newPost(recruiter, 1);
        Application accepted = apply(newUser("a1"), post, "바이올린");
        Application samePending = apply(newUser("a2"), post, "바이올린");
        Application otherPending = apply(newUser("a3"), post, "첼로");

        applicationService.accept(recruiter, accepted.getId());

        assertThat(statusOf(accepted)).isEqualTo(ApplicationStatus.ACCEPTED);
        assertThat(statusOf(samePending)).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(statusOf(otherPending)).isEqualTo(ApplicationStatus.PENDING);
        // 수락 알림 1건 + 자동 거절 알림 1건
        assertThat(events.stream(ApplicationResultNotificationEvent.class).map(ApplicationResultNotificationEvent::accepted))
                .containsExactlyInAnyOrder(true, false);
    }

    // 정원이 남아 있으면 같은 악기의 대기 지원도 건드리지 않는다
    @Test
    void accept_keepsOtherPendingApplicationsWhileInstrumentStillHasRoom() {
        User recruiter = newUser("recruiter");
        Post post = newPost(recruiter, 2);
        Application accepted = apply(newUser("a1"), post, "바이올린");
        Application samePending = apply(newUser("a2"), post, "바이올린");

        applicationService.accept(recruiter, accepted.getId());

        assertThat(statusOf(samePending)).isEqualTo(ApplicationStatus.PENDING);
        assertThat(events.stream(ApplicationResultNotificationEvent.class).count()).isEqualTo(1);
    }

    // 자동 거절된 지원은 수락이 철회돼 자리가 다시 열려도 복구되지 않는다
    @Test
    void revoke_doesNotRestoreAutoRejectedApplications() {
        User recruiter = newUser("recruiter");
        Post post = newPost(recruiter, 1);
        Application accepted = apply(newUser("a1"), post, "바이올린");
        Application samePending = apply(newUser("a2"), post, "바이올린");
        applicationService.accept(recruiter, accepted.getId());

        applicationService.revoke(recruiter, accepted.getId());

        assertThat(statusOf(samePending)).isEqualTo(ApplicationStatus.REJECTED);
    }
}
