package com.obri_back.obri.notification;

import com.obri_back.obri.notification.event.ApplicationResultNotificationEvent;
import com.obri_back.obri.notification.event.NewApplicationNotificationEvent;
import com.obri_back.obri.notification.event.NewPostNotificationEvent;
import com.obri_back.obri.notification.event.PostDeletedNotificationEvent;
import com.obri_back.obri.notification.event.PostUpdatedNotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 이벤트 → NotificationService 호출로 정확히 위임되는지만 검증
// (AFTER_COMMIT 타이밍 자체는 NotificationAfterCommitTest에서 별도 검증)
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock NotificationService notificationService;
    @InjectMocks NotificationEventListener listener;

    @Test
    void onNewApplication_delegatesToNotifyNewApplication() {
        listener.onNewApplication(new NewApplicationNotificationEvent("recruiter-token", 1L, "현악 앙상블 단원 모집"));

        verify(notificationService, times(1)).notifyNewApplication("recruiter-token", 1L, "현악 앙상블 단원 모집");
    }

    @Test
    void onNewPost_delegatesToNotifyNewPost() {
        listener.onNewPost(new NewPostNotificationEvent(1L, "현악 앙상블 단원 모집"));

        verify(notificationService, times(1)).notifyNewPost(1L, "현악 앙상블 단원 모집");
    }

    @Test
    void onApplicationResult_delegatesToNotifyResult() {
        listener.onApplicationResult(new ApplicationResultNotificationEvent("applicant-token", true));

        verify(notificationService, times(1)).notifyResult("applicant-token", true);
    }

    @Test
    void onPostUpdated_delegatesToNotifyPostUpdated() {
        listener.onPostUpdated(new PostUpdatedNotificationEvent(List.of("token-a"), 1L, "수정된 제목"));

        verify(notificationService, times(1)).notifyPostUpdated(List.of("token-a"), 1L, "수정된 제목");
    }

    @Test
    void onPostDeleted_delegatesToNotifyPostDeleted() {
        listener.onPostDeleted(new PostDeletedNotificationEvent(List.of("accepted-token"), 1L, "현악 앙상블 단원 모집"));

        verify(notificationService, times(1)).notifyPostDeleted(List.of("accepted-token"), 1L, "현악 앙상블 단원 모집");
    }
}
