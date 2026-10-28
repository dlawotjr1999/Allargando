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
                ConcertSpecification.filter(null, List.of("서울특별시"), null, null, false, null));

        assertThat(result).extracting(Concert::getRegion).containsExactly("서울특별시");
    }

    @Test
    void filter_multipleRegionsActAsOr() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));
        persistConcert("PF2", "서양음악(클래식)", "경기도", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));
        persistConcert("PF3", "서양음악(클래식)", "부산광역시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));

        List<Concert> result = concertRepository.findAll(
                ConcertSpecification.filter(null, List.of("서울특별시", "경기도"), null, null, false, null));

        assertThat(result).extracting(Concert::getRegion).containsExactlyInAnyOrder("서울특별시", "경기도");
    }

    @Test
    void filter_matchesCategory() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));
        persistConcert("PF2", "대중음악", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));

        List<Concert> result = concertRepository.findAll(
                ConcertSpecification.filter(List.of("서양음악(클래식)"), null, null, null, false, null));

        assertThat(result).extracting(Concert::getCategory).containsExactly("서양음악(클래식)");
    }

    @Test
    void filter_excludesConcertsThatAlreadyEnded() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().minusDays(10), LocalDate.now().minusDays(9));
        persistConcert("PF2", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(10), LocalDate.now().plusDays(10));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null, false, null));

        assertThat(result).extracting(Concert::getExternalId).containsExactly("PF2");
    }

    // 조회 기간과 공연 기간이 하루라도 겹치면 포함 — 기간 전에 시작해 기간 중에도 계속되는 공연(RUNNING)도 나온다
    @Test
    void filter_matchesConcertsOverlappingFromAndToDate() {
        LocalDate from = LocalDate.now().plusDays(5);
        LocalDate to = LocalDate.now().plusDays(10);
        persistConcert("INSIDE", "서양음악(클래식)", "서울특별시", from.plusDays(1), from.plusDays(1));
        persistConcert("RUNNING", "서양음악(클래식)", "서울특별시", LocalDate.now().minusDays(3), from.plusDays(2));
        persistConcert("ENDS_ON_FROM", "서양음악(클래식)", "서울특별시", LocalDate.now(), from);
        persistConcert("STARTS_ON_TO", "서양음악(클래식)", "서울특별시", to, to.plusDays(3));
        persistConcert("ENDS_BEFORE", "서양음악(클래식)", "서울특별시", LocalDate.now(), from.minusDays(1));
        persistConcert("STARTS_AFTER", "서양음악(클래식)", "서울특별시", to.plusDays(1), to.plusDays(1));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, from, to, false, null));

        assertThat(result).extracting(Concert::getExternalId)
                .containsExactlyInAnyOrder("INSIDE", "RUNNING", "ENDS_ON_FROM", "STARTS_ON_TO");
    }

    @Test
    void filter_noParams_returnsAllUpcomingConcerts() {
        persistConcert("PF1", "서양음악(클래식)", "서울특별시", LocalDate.now().plusDays(5), LocalDate.now().plusDays(5));
        persistConcert("PF2", "대중음악", "경기도", LocalDate.now().plusDays(20), LocalDate.now().plusDays(20));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null, false, null));

        assertThat(result).hasSize(2);
    }

    // 기본 정렬: 시작 전 공연(시작일 순)이 먼저, 진행 중인 공연(종료일 순)은 그 뒤 — 오래전에 시작한 장기 공연이
    // 목록 맨 위를 차지하지 않게. 오늘 시작하는 공연은 시작 전으로 친다
    @Test
    void filter_ordersUpcomingByStartDateThenRunningByEndDate() {
        LocalDate today = LocalDate.now();
        persistConcert("RUN_LONG", "서양음악(클래식)", "서울특별시", today.minusYears(1), today.plusDays(60));
        persistConcert("RUN_SHORT", "서양음악(클래식)", "서울특별시", today.minusDays(3), today.plusDays(2));
        persistConcert("SOON", "서양음악(클래식)", "서울특별시", today.plusDays(1), today.plusDays(1));
        persistConcert("TODAY", "서양음악(클래식)", "서울특별시", today, today);
        persistConcert("LATER", "서양음악(클래식)", "서울특별시", today.plusDays(30), today.plusDays(30));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null, false, null));

        assertThat(result).extracting(Concert::getExternalId)
                .containsExactly("TODAY", "SOON", "LATER", "RUN_SHORT", "RUN_LONG");
    }

    // running이면 진행 중인 공연(시작일 지남·종료일 안 지남)만, 곧 끝나는 순으로
    @Test
    void filter_runningReturnsOnlyStartedConcertsByEndDate() {
        LocalDate today = LocalDate.now();
        persistConcert("RUN_LONG", "서양음악(클래식)", "서울특별시", today.minusYears(1), today.plusDays(60));
        persistConcert("RUN_ENDS_TODAY", "서양음악(클래식)", "서울특별시", today.minusDays(3), today);
        persistConcert("TODAY", "서양음악(클래식)", "서울특별시", today, today.plusDays(5));
        persistConcert("ENDED", "서양음악(클래식)", "서울특별시", today.minusDays(10), today.minusDays(1));

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null, true, null));

        assertThat(result).extracting(Concert::getExternalId).containsExactly("RUN_ENDS_TODAY", "RUN_LONG");
    }

    // 검색 테스트용 — 제목·공연장을 직접 지정
    private void persistNamedConcert(String externalId, String title, String venue) {
        LocalDate day = LocalDate.now().plusDays(10);
        entityManager.persist(Concert.fromSync(externalId, title, "서양음악(클래식)", day, day,
                venue, "서울특별시", null, "https://example.com/" + externalId));
    }

    // 검색어는 제목 또는 공연장에 들어 있으면 일치하고, 영문 대소문자는 가리지 않는다
    @Test
    void filter_keywordMatchesTitleOrVenueIgnoringCase() {
        persistNamedConcert("TITLE", "Jazz Night 정기공연", "성수 아트홀");
        persistNamedConcert("VENUE", "가을 실내악", "JAZZ 클럽 블루");
        persistNamedConcert("NONE", "베토벤 교향곡", "예술의전당");

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null, false, "jazz"));

        assertThat(result).extracting(Concert::getExternalId).containsExactlyInAnyOrder("TITLE", "VENUE");
    }

    // 검색어의 %·_는 와일드카드가 아니라 글자 그대로 찾는다 — "100%"가 "100점"에 걸리지 않게
    @Test
    void filter_keywordTreatsLikeWildcardsLiterally() {
        persistNamedConcert("PERCENT", "할인 100% 콘서트", "어느 공연장");
        persistNamedConcert("OTHER", "100점 콘서트", "어느 공연장");

        List<Concert> result = concertRepository.findAll(ConcertSpecification.filter(null, null, null, null, false, "100%"));

        assertThat(result).extracting(Concert::getExternalId).containsExactly("PERCENT");
    }
}
