package com.wangnu.allargando.practice.repository;

import com.wangnu.allargando.global.common.KeywordSearch;
import com.wangnu.allargando.practice.entity.PracticeLog;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/*
 * 내 연습 일지 목록 동적 필터 명세 빌더
 * 항상 작성자 본인 것만 조회한다(공개 목록 없음). 정렬은 컨트롤러의 Pageable이 정한다
 */
public class PracticeLogSpecification {

    // 작성자 + 검색어 조건을 조합한 Specification 생성.
    // keyword는 제목·내용 부분 검색(KeywordSearch 규칙). null이면 조건 없음 — 정리·검증은 서비스가 한다
    public static Specification<PracticeLog> filter(Long userId, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (keyword != null) {
                predicates.add(KeywordSearch.containsAny(cb, keyword, root.get("title"), root.get("content")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
