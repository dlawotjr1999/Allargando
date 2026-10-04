package com.wangnu.allargando.post.entity;

// 모집글 전체 모집 상태 — 악기별 마감 현황으로부터 파생
public enum PostStatus {
    OPEN,               // 모집중
    PARTIALLY_CLOSED,   // 부분마감(일부 악기만 확정 완료)
    CLOSED              // 전체마감(모든 악기 확정)
}
