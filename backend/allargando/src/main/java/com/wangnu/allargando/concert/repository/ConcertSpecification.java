package com.wangnu.allargando.concert.repository;

import com.wangnu.allargando.concert.entity.Concert;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Concert 목록 동적 필터 명세 빌더
 * GET /api/concerts 필터: 필터 간 AND, 같은 필터 내 다중값은 OR
 * endDate가 지난 공연은 항상 제외(이미 끝난 공연을 목록에 노출하지 않기 위함 — PostSpecification의
 * eventAt 제외 규칙과 동일한 취지)
 */
public class ConcertSpecification {

    // 카테고리(장르)·지역·기간 조건을 조합한 Specification 생성
    // fromDate/toDate는 "공연 시작일이 이 범위 안에 있는 공연을 보고 싶다"는 조회 기간 — Concert 엔티티 자체의
    // startDate/endDate(공연 자체의 시작·종료일)와 이름이 겹치지 않도록 구분해서 명명
    public static Specification<Concert> filter(List<String> categories, List<String> regions,
            LocalDate fromDate, LocalDate toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 이미 종료된 공연은 항상 제외
            predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), LocalDate.now()));

            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").in(categories));
            }

            if (regions != null && !regions.isEmpty()) {
                predicates.add(root.get("region").in(regions));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startDate"), fromDate));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
