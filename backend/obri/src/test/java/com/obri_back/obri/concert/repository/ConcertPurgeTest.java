package com.obri_back.obri.concert.repository;

import com.obri_back.obri.concert.entity.Concert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// deleteByEndDateBefore를 실제 쿼리로 검증한다(H2) — 경계(기준일 당일 종료는 남고 하루 전 종료는 삭제)와 삭제 건수
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConcertPurgeTest {

    @Autowired TestEntityManager entityManager;
    @Autowired ConcertRepository concertRepository;

    private void persist(String externalId, LocalDate endDate) {
        entityManager.persist(Concert.fromSync(externalId, "제목-" + externalId, "서양음악(클래식)",
                endDate.minusDays(1), endDate, "공연장", "서울특별시", null, "https://example.com/" + externalId));
    }

    @Test
    void deleteByEndDateBefore_deletesOnlyConcertsEndedBeforeCutoff() {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        persist("OLD91", cutoff.minusDays(1));  // 기준일보다 하루 이전에 종료 → 삭제
        persist("EDGE90", cutoff);               // 기준일 당일 종료 → 유지
        persist("RECENT", LocalDate.now().minusDays(89));
        persist("UPCOMING", LocalDate.now().plusDays(10));
        entityManager.flush();

        int deleted = concertRepository.deleteByEndDateBefore(cutoff);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(concertRepository.findByExternalId("OLD91")).isEmpty();
        assertThat(concertRepository.findByExternalId("EDGE90")).isPresent();
        assertThat(concertRepository.findByExternalId("RECENT")).isPresent();
        assertThat(concertRepository.findByExternalId("UPCOMING")).isPresent();
    }
}
