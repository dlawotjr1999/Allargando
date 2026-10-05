package com.wangnu.allargando.notification.event;

import java.util.List;

// FCM이 "더 이상 유효하지 않다(UNREGISTERED)"고 응답한 토큰 목록 — 앱 삭제·재설치로 죽은 토큰을 DB에서 비우게 한다.
// NotificationService는 도메인에 의존하지 않는 리프 모듈이라 직접 지우지 않고 이벤트로 알린다
public record StaleFcmTokensEvent(List<String> fcmTokens) {
}
