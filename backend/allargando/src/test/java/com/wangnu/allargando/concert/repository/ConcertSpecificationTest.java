package com.wangnu.allargando.concert.repository;

import com.wangnu.allargando.concert.entity.Concert;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ConcertSpecification의 region·category 필터는 Criteria API 조건이라 실제 쿼리 실행 없이는 검증 불가 → @DataJpaTest(내장 H2)
// 아래 3개 프로퍼티는 PostSpecificationTest와 동일한 이유로 필요:
// globally_quoted_identifiers=true — application.properties 전역 설정과 스키마 생성 방식을 맞추기 위함
// spring.flyway.enabled=false — Flyway가 PostgreSQL 문법 마이그레이션을 H2 스키마에 적용하려 들면 충돌하므로 비활성화
// ddl-auto=create-drop — application.properties의 validate가 @DataJpaTest 기본값을 덮어써 빈 스키마 validate 실패로 이어지는 문제 회피
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConcertSpecificationTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private ConcertRepository concertRepository;

    private void persistConcert(String externalId, String category, String region,
            LocalDate startDate, LocalDate endDate) {
        Concert concert = Concert.fromSync(externalId, "제목-" + externalId, category, startDate, endDate,
                "어느 공연장", region, null, "https://example.com/" + externalId);
        entityManager.persist(concert);
    }

    @Test
    void filter_matchesExactRegion() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));
        persistConcert("PF2", "서양음악(클래식)", "경기도", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));

        List<Concert> result = concertRepository.findAll(
                ConcertSpecification.filter(null, List.of("서울특별시"), null, null));

        assertThat(result).extracting(Concert::getRegion).containsExactly("서울특별시");
    }

    @Test
    void filter_multipleRegionsActAsOr() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));
        persistConcert("PF2", "서양음악(클래식)", "경기도", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));
        persistConcert("PF3", "서양음악(클래식)", "부산광역시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));

        List<Concert> result = concertRepository.findAll(
                ConcertSpecification.filter(null, List.of("서울특별시", "경기도"), null, null));

        assertThat(result).extracting(Concert::getRegion).containsExactlyInAnyOrder("서울특별시", "경기도");
    }

    @Test
    void filter_matchesCategory() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));
        persistConcert("PF2", "대중음악", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));

        List<Concert> result = concertRepository.findAll(
                ConcertSpecification.filter(List.of("서양음악(클래식)"), null, null, null));

        assertThat(result).extracting(Concert::getCategory).containsExactly("서양음악(클래식)");
    }

    @Test
    void filter_excludesConcertsThatAlreadyEnded() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().minusDays(10), LocalDate.now().minusDays(9));
        persistConcert("PF2", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null));

        assertThat(result).extracting(Concert::getExternalId).containsExactly("PF2");
    }

    @Test
    void filter_matchesStartDateWithinFromAndToDate() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(5), LocalDate.now().plusDays(5));
        persistConcert("PF2", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(20), LocalDate.now().plusDays(20));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(
                null, null, LocalDate.now(), LocalDate.now().plusDays(10)));

        assertThat(result).extracting(Concert::getExternalId).containsExactly("PF1");
    }

    @Test
    void filter_noParams_returnsAllUpcomingConcerts() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(5), LocalDate.now().plusDays(5));
        persistConcert("PF2", "대중음악", "경기도", LocalDate.now().plusDays(20), LocalDate.now().plusDays(20));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null));

        assertThat(result).hasSize(2);
    }
}
