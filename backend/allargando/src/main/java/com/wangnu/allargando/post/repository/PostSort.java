package com.wangnu.allargando.post.repository;

/*
 * 모집글 목록 정렬 기준(D15). 클라이언트가 임의 필드로 정렬하지 못하도록(없는 속성은 500, 개인정보 컬럼 추측)
 * 허용하는 값을 enum 화이트리스트로 둔다. 목록 밖의 값은 400.
 */
public enum PostSort {
    // 최근 등록순(기본)
    LATEST,
    // 공연 임박순 — 공연일이 가까운 글 먼저
    EVENT_SOON,
    // 마감 임박순 — 남은 자리(모집 인원 - 확정 인원의 합)가 적은 글 먼저
    CLOSING_SOON
}
