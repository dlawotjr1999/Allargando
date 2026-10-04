package com.wangnu.allargando.user.event;

// 회원 탈퇴 발생 — User 도메인이 발행하고, 각 도메인(post·application·practice)이 자기 데이터를 정리하며
// auth가 커밋 후 Firebase 계정을 삭제한다. User가 다른 도메인에 의존하지 않도록 이벤트로 역전시킨 구조(CLAUDE.md §1)
public record UserWithdrawalEvent(Long userId, String firebaseUid) {
}
