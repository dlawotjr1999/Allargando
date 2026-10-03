package com.obri_back.obri.application.entity;

// 지원 상태 — 전이는 행위자·출발 상태별로 고정 (명세 Application §2.4)
public enum ApplicationStatus {
    PENDING,    // 대기 (제출 직후 기본값)
    ACCEPTED,   // 수락 (모집자, PENDING에서)
    REJECTED,   // 거절 (모집자, PENDING에서)
    CANCELLED,  // 취소 (지원자, PENDING에서만)
    REVOKED;    // 철회 (모집자, ACCEPTED에서만; 확정 취소·자리 재오픈)

    // 모집자에게 지원자 전화번호를 그대로 보여 주는 상태 — 진행 중(PENDING)·확정(ACCEPTED)만(D13).
    // 거절·취소·철회로 끝난 지원은 연락할 이유가 없으므로 마스킹한다
    public boolean exposesApplicantPhone() {
        return this == PENDING || this == ACCEPTED;
    }
}
