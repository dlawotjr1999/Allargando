package com.obri_back.obri.user.service;

import com.obri_back.obri.application.entity.Application;
import com.obri_back.obri.application.entity.ApplicationStatus;
import com.obri_back.obri.application.service.ApplicationAccessPolicy;
import com.obri_back.obri.application.service.ApplicationService;
import com.obri_back.obri.block.entity.UserBlock;
import com.obri_back.obri.block.service.BlockService;
import com.obri_back.obri.post.entity.Post;
import com.obri_back.obri.post.entity.PostInfo;
import com.obri_back.obri.post.entity.PostInstrument;
import com.obri_back.obri.post.entity.PostStatus;
import com.obri_back.obri.post.service.PostService;
import com.obri_back.obri.practice.entity.PracticeLog;
import com.obri_back.obri.practice.service.PracticeLogService;
import com.obri_back.obri.report.entity.Report;
import com.obri_back.obri.report.entity.ReportReason;
import com.obri_back.obri.report.entity.ReportTargetType;
import com.obri_back.obri.report.service.ReportService;
import com.obri_back.obri.user.entity.Career;
import com.obri_back.obri.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 회원 탈퇴가 실제 DB 제약(FK) 아래에서 끝까지 성공하는지 검증 — 단위 테스트(Mockito)로는 "유저 행보다 먼저 지워야
// FK 위반이 없다"는 삭제 순서를 확인할 수 없어, 진짜 서비스·리포지토리·이벤트 리스너를 엮어 H2에서 실행한다.
// (Firebase 삭제는 AFTER_COMMIT 리스너라 이 테스트(롤백 트랜잭션)에서는 실행되지 않으며 AuthServiceTest가 따로 검증한다)
// 설정 프로퍼티의 의도는 PostSpecificationTest 주석 참고(H2 예약어·Flyway·ddl-auto 우회)
@DataJpaTest
@Import({UserService.class, PostService.class, ApplicationService.class,
        ApplicationAccessPolicy.class, PracticeLogService.class, BlockService.class, ReportService.class})
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserWithdrawalFlowTest {

    @Autowired TestEntityManager em;
    @Autowired UserService userService;

    private User newUser(String name) {
        return em.persist(User.builder()
                .firebaseUid(name + "-uid")
                .phoneNumber("010-0000-" + String.format("%04d", Math.abs(name.hashCode()) % 10000))
                .nickname(name)
                .instrument("바이올린")
                .build());
    }

    private Post newPost(User owner, String title) {
        Post post = Post.create(owner, PostInfo.builder()
                .category("앙상블")
                .title(title)
                .eventAt(LocalDateTime.now().plusDays(7))
                .location("서울 강남구")
                .region("서울")
                .timetable("13:00")
                .build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 2));
        return em.persist(post);
    }

    private Application newApplication(User applicant, Post post, ApplicationStatus status) {
        return em.persist(Application.builder()
                .user(applicant)
                .post(post)
                .instrument("바이올린")
                .status(status)
                .build());
    }

    @Test
    void deleteUser_removesEverythingTheUserLeftBehindWithoutBreakingOthers() {
        User leaver = newUser("leaver");
        User recruiter = newUser("recruiter");
        User other = newUser("other");

        // 탈퇴자가 쓴 글 + 다른 사람의 지원서
        Post leaversPost = newPost(leaver, "탈퇴자의 글");
        Application othersApplication = newApplication(other, leaversPost, ApplicationStatus.ACCEPTED);

        // 다른 사람의 글 + 탈퇴자의 수락된 지원 (확정 인원 1명으로 반영)
        Post recruitersPost = newPost(recruiter, "모집자의 글");
        recruitersPost.confirmInstrument("바이올린");
        Application leaversApplication = newApplication(leaver, recruitersPost, ApplicationStatus.ACCEPTED);

        // 탈퇴자가 차단한 기록·탈퇴자가 차단당한 기록, 탈퇴자가 한 신고·탈퇴자를 대상으로 한 신고
        UserBlock leaverBlocksOther = em.persist(UserBlock.of(leaver, other));
        UserBlock otherBlocksLeaver = em.persist(UserBlock.of(other, leaver));
        Report reportByLeaver = em.persist(
                Report.create(leaver, ReportTargetType.POST, recruitersPost.getId(), ReportReason.SPAM, null));
        Report reportAgainstLeaver = em.persist(
                Report.create(other, ReportTargetType.USER, leaver.getId(), ReportReason.HARASSMENT, "상세"));
        // 탈퇴자와 무관한 신고·차단은 그대로 남아야 한다
        UserBlock unrelatedBlock = em.persist(UserBlock.of(other, recruiter));
        Report unrelatedReport = em.persist(
                Report.create(other, ReportTargetType.POST, recruitersPost.getId(), ReportReason.FRAUD, null));

        // 탈퇴자의 경력·연습일지
        em.persist(Career.of(leaver, "서울시향", "객원"));
        PracticeLog log = em.persist(PracticeLog.create(leaver, "연습", LocalDate.now(), 30, "내용"));

        Long leaverId = leaver.getId();
        Long leaversPostId = leaversPost.getId();
        Long othersApplicationId = othersApplication.getId();
        Long leaversApplicationId = leaversApplication.getId();
        Long logId = log.getId();
        Long recruitersPostId = recruitersPost.getId();
        Long leaverBlocksOtherId = leaverBlocksOther.getId();
        Long otherBlocksLeaverId = otherBlocksLeaver.getId();
        Long reportByLeaverId = reportByLeaver.getId();
        Long reportAgainstLeaverId = reportAgainstLeaver.getId();
        Long unrelatedBlockId = unrelatedBlock.getId();
        Long unrelatedReportId = unrelatedReport.getId();
        // 영속성 컨텍스트를 비워, 서비스가 실제 운영처럼 DB에서 새로 로딩하게 한다(경력 컬렉션 cascade 포함)
        em.flush();
        em.clear();

        userService.deleteUser(User.builder().id(leaverId).build());
        em.flush(); // 여기서 FK 위반이 있으면 예외가 난다
        em.clear();

        // 탈퇴자의 모든 흔적이 사라짐
        assertThat(em.find(User.class, leaverId)).isNull();
        assertThat(em.find(Post.class, leaversPostId)).isNull();
        assertThat(em.find(Application.class, othersApplicationId)).isNull();
        assertThat(em.find(Application.class, leaversApplicationId)).isNull();
        assertThat(em.find(PracticeLog.class, logId)).isNull();
        assertThat(em.find(UserBlock.class, leaverBlocksOtherId)).isNull();
        assertThat(em.find(UserBlock.class, otherBlocksLeaverId)).isNull();
        assertThat(em.find(Report.class, reportByLeaverId)).isNull();
        assertThat(em.find(Report.class, reportAgainstLeaverId)).isNull();

        // 탈퇴자와 무관한 신고·차단은 그대로
        assertThat(em.find(UserBlock.class, unrelatedBlockId)).isNotNull();
        assertThat(em.find(Report.class, unrelatedReportId)).isNotNull();

        // 다른 사람의 글은 남고, 탈퇴자가 차지했던 자리는 다시 열림
        Post remaining = em.find(Post.class, recruitersPostId);
        assertThat(remaining).isNotNull();
        assertThat(remaining.getStatus()).isEqualTo(PostStatus.OPEN);
        assertThat(remaining.getPostInstruments().get(0).getConfirmed()).isZero();

        // 다른 유저는 그대로
        assertThat(em.find(User.class, recruiter.getId())).isNotNull();
        assertThat(em.find(User.class, other.getId())).isNotNull();
    }

    @Test
    void deleteUser_succeedsForUserWithNoData() {
        User lonely = newUser("lonely");
        Long id = lonely.getId();
        em.flush();
        em.clear();

        userService.deleteUser(User.builder().id(id).build());
        em.flush();
        em.clear();

        assertThat(em.find(User.class, id)).isNull();
    }
}
