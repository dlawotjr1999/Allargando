package com.wangnu.allargando.practice.repository;

import com.wangnu.allargando.practice.entity.PracticeLog;
import com.wangnu.allargando.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// PracticeLogSpecification은 Criteria API 조건이라 실제 쿼리 실행 없이는 검증 불가 → @DataJpaTest(내장 H2)
// 설정 프로퍼티의 의도는 PostSpecificationTest 주석 참고(H2 예약어·Flyway·ddl-auto 우회)
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PracticeLogSpecificationTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private PracticeLogRepository practiceLogRepository;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-uid", "010-0000-0001", "owner");
        other = persistUser("other-uid", "010-0000-0002", "other");
    }

    private User persistUser(String uid, String phone, String nickname) {
        return entityManager.persist(User.builder().name("테스터").termsAgreedAt(LocalDateTime.now())
                .firebaseUid(uid)
                .phoneNumber(phone)
                .nickname(nickname)
                .instrument("바이올린")
                .build());
    }

    private void persistLog(User user, String title, String content) {
        persistLog(user, title, content, null);
    }

    private void persistLog(User user, String title, String content, String category) {
        entityManager.persist(PracticeLog.create(user, title, LocalDate.of(2026, 10, 1), 30, content, category));
    }

    // 검색어가 없어도 내 일지만 나온다
    @Test
    void filter_returnsOnlyOwnLogs() {
        persistLog(owner, "내 일지", null);
        persistLog(other, "남의 일지", null);

        List<PracticeLog> result = practiceLogRepository.findAll(PracticeLogSpecification.filter(owner.getId(), null, null));

        assertThat(result).extracting(PracticeLog::getTitle).containsExactly("내 일지");
    }

    // 검색어는 제목 또는 내용에 들어 있으면 일치하고(내용이 없는 일지도 제목으로 찾힌다), 남의 일지는 찾지 않는다
    @Test
    void filter_keywordMatchesTitleOrContentOfOwnLogsOnly() {
        persistLog(owner, "Bach 샤콘느", null);
        persistLog(owner, "스케일", "bach 인벤션 복습");
        persistLog(owner, "모차르트", "활 배분 연습");
        persistLog(other, "Bach 파르티타", null);

        List<PracticeLog> result = practiceLogRepository.findAll(PracticeLogSpecification.filter(owner.getId(), null, "BACH"));

        assertThat(result).extracting(PracticeLog::getTitle).containsExactlyInAnyOrder("Bach 샤콘느", "스케일");
    }

    // 분류를 고르면 그 분류의 일지만, 고르지 않으면 분류하지 않은 일지까지 모두 나온다
    @Test
    void filter_categoryNarrowsLogsAndEmptyMeansAll() {
        persistLog(owner, "스케일", null, "기본기");
        persistLog(owner, "샤콘느", null, "곡 연습");
        persistLog(owner, "분류 없음", null, null);

        assertThat(practiceLogRepository.findAll(PracticeLogSpecification.filter(owner.getId(), "기본기", null)))
                .extracting(PracticeLog::getTitle).containsExactly("스케일");
        assertThat(practiceLogRepository.findAll(PracticeLogSpecification.filter(owner.getId(), "", null)))
                .extracting(PracticeLog::getTitle).containsExactlyInAnyOrder("스케일", "샤콘느", "분류 없음");
    }
}
