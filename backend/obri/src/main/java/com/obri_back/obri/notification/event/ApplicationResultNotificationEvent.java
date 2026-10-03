package com.obri_back.obri.notification.event;

// 지원 결과(수락/거절) 알림 발송 의도 — 커밋 후(AFTER_COMMIT)에만 실제 발송
public record ApplicationResultNotificationEvent(String applicantFcmToken, boolean accepted) {
}
