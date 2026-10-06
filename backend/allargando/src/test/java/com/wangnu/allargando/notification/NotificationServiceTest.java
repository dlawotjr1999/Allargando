package com.wangnu.allargando.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.wangnu.allargando.notification.event.StaleFcmTokensEvent;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock FirebaseMessaging firebaseMessaging;
    @Mock ApplicationEventPublisher eventPublisher;
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

    @Test
    void notifyNewPost_swallowsSendFailure() throws Exception {
        when(firebaseMessaging.send(any(Message.class))).thenThrow(new RuntimeException("FCM down"));

        notificationService.notifyNewPost(1L, "현악 앙상블 단원 모집");

        verify(firebaseMessaging, times(1)).send(any(Message.class));
    }

    @Test
    void notifyNewApplication_skipsWhenTokenBlank() {
        notificationService.notifyNewApplication("  ", 1L, "현악 앙상블 단원 모집");

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void notifyPostUpdated_sendsMulticastWhenTokensPresent() throws Exception {
        notificationService.notifyPostUpdated(List.of("token-a", "token-b"), 1L, "수정된 제목");

        verify(firebaseMessaging, times(1)).sendEachForMulticast(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void notifyPostUpdated_swallowsSendFailure() throws Exception {
        when(firebaseMessaging.sendEachForMulticast(any())).thenThrow(new RuntimeException("FCM down"));

        notificationService.notifyPostUpdated(List.of("token-a"), 1L, "수정된 제목");

        verify(firebaseMessaging, times(1)).sendEachForMulticast(any());
    }

    @Test
    void notifyPostDeleted_swallowsSendFailure() throws Exception {
        when(firebaseMessaging.sendEachForMulticast(any())).thenThrow(new RuntimeException("FCM down"));

        notificationService.notifyPostDeleted(List.of("token-a"), 1L, "현악 앙상블 단원 모집");

        verify(firebaseMessaging, times(1)).sendEachForMulticast(any());
    }

    // FCM multicast 한도는 한 번에 500개라, 토큰이 501개면 500개 + 1개로 나눠 두 번 보내고 예외가 밖으로 나가지 않는다
    @Test
    void notifyPostUpdated_splitsTokensIntoBatchesOf500() throws Exception {
        List<String> tokens = IntStream.range(0, 501).mapToObj(i -> "token-" + i).toList();

        notificationService.notifyPostUpdated(tokens, 1L, "수정된 제목");

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging, times(2)).sendEachForMulticast(captor.capture());
    }

    // 첫 묶음 발송이 예외로 실패해도 거기서 멈추지 않고 다음 묶음을 계속 보낸다
    @Test
    void notifyPostUpdated_continuesWithNextBatchWhenOneFails() throws Exception {
        List<String> tokens = IntStream.range(0, 501).mapToObj(i -> "token-" + i).toList();
        when(firebaseMessaging.sendEachForMulticast(any()))
                .thenThrow(new RuntimeException("FCM down"))
                .thenReturn(mock(BatchResponse.class));

        notificationService.notifyPostUpdated(tokens, 1L, "수정된 제목");

        verify(firebaseMessaging, times(2)).sendEachForMulticast(any());
    }

    // 응답이 UNREGISTERED(죽은 토큰)인 토큰만 정리 이벤트로 알리고, UNAVAILABLE 같은 일시 오류 토큰은 건드리지 않는다
    @Test
    void notifyPostUpdated_publishesStaleEventOnlyForUnregisteredTokens() throws Exception {
        SendResponse ok = mock(SendResponse.class);
        SendResponse unregistered = mock(SendResponse.class);
        SendResponse unavailable = mock(SendResponse.class);
        FirebaseMessagingException gone = mock(FirebaseMessagingException.class);
        FirebaseMessagingException busy = mock(FirebaseMessagingException.class);
        when(gone.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(busy.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(unregistered.getException()).thenReturn(gone);
        when(unavailable.getException()).thenReturn(busy);
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getFailureCount()).thenReturn(2);
        when(batch.getResponses()).thenReturn(List.of(ok, unregistered, unavailable));
        when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(batch);

        notificationService.notifyPostUpdated(List.of("token-ok", "token-dead", "token-busy"), 1L, "수정된 제목");

        ArgumentCaptor<StaleFcmTokensEvent> captor = ArgumentCaptor.forClass(StaleFcmTokensEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().fcmTokens()).containsExactly("token-dead");
    }
}
