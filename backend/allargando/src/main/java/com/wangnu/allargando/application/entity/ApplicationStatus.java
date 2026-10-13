package com.wangnu.allargando.application.entity;

// 지원 상태 — 전이는 행위자·출발 상태별로 고정 (명세 Application §2.4)
public enum ApplicationStatus {
    PENDING,    // 대기 (제출 직후 기본값)
    ACCEPTED,   // 수락 (모집자, PENDING에서)
    REJECTED,   // 거절 (모집자, PENDING에서)
    CANCELLED,  // 취소 (지원자, PENDING에서만)
    REVOKED;    // 철회 (모집자, ACCEPTED에서만; 확정 취소·자리 재오픈)

    // 모집자에게 지원자의 이름·전화번호를 그대로 보여 주는 상태 — 수락(ACCEPTED)된 지원만.
    // 대기 중에는 가린다: 글을 올려 지원만 받고 연락처를 모으는 남용을 막기 위해서다(모집자는 닉네임·악기·경력·
    // 지원 메시지로 판단하고, 연락은 수락한 뒤에 한다). 거절·취소·철회로 끝난 지원도 연락할 이유가 없어 가린다
    public boolean exposesApplicantContact() {
        return this == ACCEPTED;
    }
}
