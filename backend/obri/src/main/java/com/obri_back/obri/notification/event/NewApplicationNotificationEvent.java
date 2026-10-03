package com.obri_back.obri.notification.event;

// 새 지원 도착 알림 발송 의도 — 커밋 후(AFTER_COMMIT)에만 실제 발송
public record NewApplicationNotificationEvent(String recruiterFcmToken, Long postId, String postTitle) {
}
