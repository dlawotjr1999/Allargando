package com.wangnu.allargando.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock FirebaseMessaging firebaseMessaging;
    @InjectMocks NotificationService notificationService;

    @Test
    void notifyResult_skipsWhenTokenNull() {
        notificationService.notifyResult(null, true);

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void notifyResult_skipsWhenTokenBlank() {
        notificationService.notifyResult("  ", true);

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void notifyResult_sendsWhenTokenPresent() throws Exception {
        notificationService.notifyResult("token-123", true);

        verify(firebaseMessaging, times(1)).send(any(Message.class));
    }

    @Test
    void notifyNewPost_sendsToTopic() throws Exception {
        notificationService.notifyNewPost(1L, "현악 앙상블 단원 모집");

        verify(firebaseMessaging, times(1)).send(any(Message.class));
    }

    @Test
    void notifyResult_swallowsSendFailure() throws Exception {
        when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(new RuntimeException("FCM down"));

        // 발송 실패가 호출자에게 전파되지 않아야 함 (예외 없이 종료)
        notificationService.notifyResult("token-123", false);

        verify(firebaseMessaging, times(1)).send(any(Message.class));
    }

    @Test
    void notifyNewApplication_skipsWhenTokenNull() {
        notificationService.notifyNewApplication(null, 1L, "현악 앙상블 단원 모집");

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void notifyNewApplication_sendsWhenTokenPresent() throws Exception {
        notificationService.notifyNewApplication("recruiter-token", 1L, "현악 앙상블 단원 모집");

        verify(firebaseMessaging, times(1)).send(any(Message.class));
    }

    @Test
    void notifyPostUpdated_skipsWhenNoTokens() {
        notificationService.notifyPostUpdated(List.of(), 1L, "수정된 제목");

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void notifyPostDeleted_skipsWhenNoTokens() {
        notificationService.notifyPostDeleted(List.of(), 1L, "현악 앙상블 단원 모집");

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void notifyPostDeleted_sendsMulticastWhenTokensPresent() throws Exception {
        notificationService.notifyPostDeleted(List.of("token-a", "token-b"), 1L, "현악 앙상블 단원 모집");

        verify(firebaseMessaging, times(1)).sendEachForMulticast(any());
    }
}
