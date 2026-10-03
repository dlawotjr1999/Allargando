package com.obri_back.obri.notification;

import com.obri_back.obri.notification.event.ApplicationResultNotificationEvent;
import com.obri_back.obri.notification.event.NewApplicationNotificationEvent;
import com.obri_back.obri.notification.event.NewPostNotificationEvent;
import com.obri_back.obri.notification.event.PostDeletedNotificationEvent;
import com.obri_back.obri.notification.event.PostUpdatedNotificationEvent;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/*
 * 알림 발송을 커밋 이후(AFTER_COMMIT)로 미루는 리스너
 * 트랜잭션이 롤백되면 이벤트 자체가 버려지므로 유령 알림(예: @Version 충돌로 롤백됐는데
 * 이미 나간 "수락되었습니다" 푸시)이 발생하지 않는다. 이벤트가 원시값만 담고 있어
 * 엔티티 detach 문제와도 무관.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewPost(NewPostNotificationEvent event) {
        notificationService.notifyNewPost(event.postId(), event.title());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewApplication(NewApplicationNotificationEvent event) {
        notificationService.notifyNewApplication(event.recruiterFcmToken(), event.postId(), event.postTitle());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationResult(ApplicationResultNotificationEvent event) {
        notificationService.notifyResult(event.applicantFcmToken(), event.accepted());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostUpdated(PostUpdatedNotificationEvent event) {
        notificationService.notifyPostUpdated(event.fcmTokens(), event.postId(), event.title());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostDeleted(PostDeletedNotificationEvent event) {
        notificationService.notifyPostDeleted(event.fcmTokens(), event.postId(), event.title());
    }
}
