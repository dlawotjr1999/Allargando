package com.obri_back.obri.global.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/*
 * 목록 API의 페이지 요청 보정 — 페이지 번호·크기만 클라이언트 값을 쓰고 정렬은 서버가 정한 값으로 고정한다.
 * Pageable을 그대로 받으면 ?sort=user.phoneNumber 같은 임의 속성 정렬이 쿼리에 들어가
 * (1) 없는 속성은 500, (2) 연관 엔티티의 개인정보 컬럼 순서로 정렬해 값을 추측할 수 있게 된다
 */
public final class PageSupport {

    private PageSupport() {
    }

    // 클라이언트가 보낸 정렬은 버리고 sort로 대체한 Pageable 반환 (page·size는 유지)
    public static Pageable withSort(Pageable pageable, Sort sort) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
