package com.wangnu.allargando.concert.repository;

import com.wangnu.allargando.concert.entity.Concert;
import com.wangnu.allargando.global.common.KeywordSearch;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Concert 목록 동적 필터 명세 빌더
 * GET /api/concerts 필터: 필터 간 AND, 같은 필터 내 다중값은 OR. 검색어는 제목 또는 공연장에 들어 있으면 일치
 * endDate가 지난 공연은 항상 제외(이미 끝난 공연을 목록에 노출하지 않기 위함 — PostSpecification의
 * eventAt 제외 규칙과 동일한 취지)
 *
 * 정렬은 쿼리에 직접 건다(PostSpecification과 같은 방식) — 호출부는 정렬 없는 Pageable을 넘겨야 이 정렬이 유지된다.
 * 시작일 순으로만 정렬하면 몇 달·몇 년째 진행 중인 장기 공연이 목록 맨 위를 차지해 다가오는 공연이 묻힌다.
 * 그래서 기본 목록은 시작 전 공연을 시작일 순으로 먼저 두고 진행 중인 공연은 그 뒤에 종료일 순으로 붙이며,
 * 진행 중인 공연만 따로 보는 running 필터를 둔다(running이면 종료 임박순)
 */
public class ConcertSpecification {

    // 카테고리(장르)·지역·기간·진행 중 조건을 조합하고 정렬까지 지정한 Specification 생성
    // fromDate/toDate는 "이 기간에 볼 수 있는 공연"을 고르는 조회 기간 — 공연 기간(startDate~endDate)이 조회 기간과
    // 하루라도 겹치면 포함한다(앱의 "이번 주말"에 지난주부터 계속되는 공연도 나오도록). Concert 엔티티 자체의
    // startDate/endDate(공연 자체의 시작·종료일)와 이름이 겹치지 않도록 구분해서 명명
    // keyword는 공연 제목·공연장 이름 부분 검색(KeywordSearch 규칙). null이면 조건 없음 — 정리·검증은 서비스가 한다
    public static Specification<Concert> filter(List<String> categories, List<String> regions,
            LocalDate fromDate, LocalDate toDate, boolean running, String keyword) {
        return (root, query, cb) -> {
            LocalDate today = LocalDate.now();
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null) {
                predicates.add(KeywordSearch.containsAny(cb, keyword, root.get("title"), root.get("venue")));
            }

            // 이미 종료된 공연은 항상 제외
            predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), today));

            // 진행 중 = 시작일이 지났고(위 조건으로) 아직 끝나지 않은 공연
            if (running) {
                predicates.add(cb.lessThan(root.get("startDate"), today));
            }

            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").in(categories));
            }

            if (regions != null && !regions.isEmpty()) {
                predicates.add(root.get("region").in(regions));
            }

            // 조회 기간과 공연 기간이 겹침: 조회 시작일 이후에 끝나고, 조회 종료일 이전에 시작한다
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), toDate));
            }

            // count 쿼리(결과 타입이 Long)에는 정렬을 걸지 않는다
            if (!Long.class.equals(query.getResultType())) {
                query.orderBy(running ? endingSoonOrder(root, cb) : upcomingFirstOrder(root, cb, today));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 기본 정렬: 시작 전 공연(시작일 순) → 진행 중인 공연(종료일 순). 같은 값끼리는 id로 순서를 고정한다
    // (PageSupport와 같은 이유 — 페이지 사이에서 같은 공연이 두 번 나오거나 빠지지 않게)
    private static List<Order> upcomingFirstOrder(Root<Concert> root, CriteriaBuilder cb, LocalDate today) {
        Expression<LocalDate> startDate = root.get("startDate");
        Expression<LocalDate> endDate = root.get("endDate");
        Predicate started = cb.lessThan(startDate, today);
        // 정렬 기준 날짜: 시작 전이면 시작일, 진행 중이면 종료일. 숫자 상수 대신 날짜 컬럼만 고르는 CASE라
        // PostgreSQL이 바인딩 값의 타입을 추론하지 못하는 문제가 없다
        Expression<LocalDate> keyDate = cb.<LocalDate>selectCase().when(started, endDate).otherwise(startDate);
        // 시작 전 공연을 먼저 — 진행 중 여부(started)를 날짜 비교 결과 그대로 정렬해 false(시작 전)가 앞에 온다
        return List.of(cb.asc(started), cb.asc(keyDate), cb.asc(root.get("id")));
    }

    // 진행 중 목록 정렬: 곧 끝나는 공연부터
    private static List<Order> endingSoonOrder(Root<Concert> root, CriteriaBuilder cb) {
        return List.of(cb.asc(root.get("endDate")), cb.asc(root.get("id")));
    }
}
