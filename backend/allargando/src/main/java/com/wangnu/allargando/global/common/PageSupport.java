package com.wangnu.allargando.global.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/*
 * 목록 API의 페이지 요청 보정 — 페이지 번호·크기만 클라이언트 값을 쓰고 정렬은 서버가 정한 값으로 고정한다.
 * Pageable을 그대로 받으면 ?sort=user.phoneNumber 같은 임의 속성 정렬이 쿼리에 들어가
 * (1) 없는 속성은 500, (2) 연관 엔티티의 개인정보 컬럼 순서로 정렬해 값을 추측할 수 있게 된다.
 *
 * 정렬 기준이 같은 값을 가진 행이 많으면(예: 같은 날 시작하는 공연, 같은 날짜의 연습일지) DB가 그 행들의 순서를
 * 요청마다 다르게 돌려줄 수 있다. 그러면 페이지 사이에서 같은 행이 두 번 나오거나 어떤 행이 빠지므로, 정렬이 있을 때는
 * 마지막에 기본키(id)를 덧붙여 순서를 항상 한 가지로 정한다.
 */
public final class PageSupport {

    // 모든 목록 엔티티(공연·지원·모집글·연습일지)의 기본키 속성 이름
    private static final String TIEBREAKER = "id";

    private PageSupport() {
    }

    // 클라이언트가 보낸 정렬은 버리고 sort로 대체한 Pageable 반환 (page·size는 유지).
    // 정렬이 있으면 id 오름차순을 마지막 기준으로 덧붙여 같은 값끼리의 순서를 고정한다(이미 id로 정렬하면 그대로 둔다).
    // 정렬이 없는 요청(Specification이 쿼리에 직접 정렬을 거는 경우)에는 아무것도 붙이지 않는다 — 붙이면 Specification의
    // 정렬이 덮어써진다
    public static Pageable withSort(Pageable pageable, Sort sort) {
        Sort ordered = sort.isSorted() && sort.getOrderFor(TIEBREAKER) == null
                ? sort.and(Sort.by(Sort.Direction.ASC, TIEBREAKER))
                : sort;
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ordered);
    }
}
