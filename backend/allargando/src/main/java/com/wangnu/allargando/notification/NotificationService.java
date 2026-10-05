package com.wangnu.allargando.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.wangnu.allargando.notification.event.StaleFcmTokensEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/*
 * FCM 알림 발송 서비스
 * - 신규 모집글: "new_post" 토픽 broadcast
 * - 지원 결과(수락/거절): fcm_token 단건 push
 * - 모집글 수정: PENDING·ACCEPTED 지원자 multicast
 * - 모집글 삭제: ACCEPTED 지원자 multicast
 * - multicast는 FCM 한도(500개)씩 나눠 보내고, UNREGISTERED로 응답한 토큰은 StaleFcmTokensEvent로 알려 정리하게 한다
 *
 * 규칙(CLAUDE.md 3.8):
 * - notification 테이블 없음. user.fcm_token만 사용
 * - fcm_token이 없으면 발송 생략
 * - 발송 실패는 try-catch로 격리해 핵심 트랜잭션에 영향 주지 않음
 *
 * 도메인 의존을 두지 않는 리프 모듈로 유지(순환 방지) — 상태는 boolean으로 전달받음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String NEW_POST_TOPIC = "new_post";

    // FCM multicast 1회 한도(Admin SDK가 초과하면 MulticastMessage 생성에서 예외를 던진다)
    private static final int MULTICAST_LIMIT = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final ApplicationEventPublisher eventPublisher;

    // 새 모집글 등록 → 전체 구독자에게 broadcast
    public void notifyNewPost(Long postId, String title) {
        Message message = Message.builder()
                .setTopic(NEW_POST_TOPIC)
                .setNotification(Notification.builder()
                        .setTitle("새 모집글이 등록되었어요")
                        .setBody(title)
                        .build())
                .putData("type", "NEW_POST")
                .putData("postId", String.valueOf(postId))
                .build();
        send(message, "신규 모집글 알림", postId);
    }

    // 지원 결과(수락/거절) → 지원자 단건 push
    public void notifyResult(String fcmToken, boolean accepted) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return; // 토큰 없으면 발송 생략
        }
        String body = accepted ? "지원이 수락되었어요" : "아쉽지만 이번 지원은 반영되지 않았어요";
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle("지원 결과 알림")
                        .setBody(body)
                        .build())
                .putData("type", "APPLICATION_RESULT")
                .putData("accepted", String.valueOf(accepted))
                .build();
        send(message, "지원 결과 알림", null);
    }

    // 새 지원 도착 → 모집글 작성자(모집자) 단건 push
    public void notifyNewApplication(String recruiterFcmToken, Long postId, String postTitle) {
        if (recruiterFcmToken == null || recruiterFcmToken.isBlank()) {
            return; // 토큰 없으면 발송 생략
        }
        Message message = Message.builder()
                .setToken(recruiterFcmToken)
                .setNotification(Notification.builder()
                        .setTitle("새 지원이 도착했어요")
                        .setBody(postTitle)
                        .build())
                .putData("type", "NEW_APPLICATION")
                .putData("postId", String.valueOf(postId))
                .build();
        send(message, "새 지원 알림", postId);
    }

    // 모집글 수정 → 대기·수락 지원자에게 multicast
    public void notifyPostUpdated(List<String> fcmTokens, Long postId, String title) {
        sendMulticast(fcmTokens, "지원한 모집글이 수정되었어요", title, "POST_UPDATED", postId, "모집글 수정 알림");
    }

    // 모집글 삭제 → 확정(ACCEPTED)된 지원자에게 multicast
    public void notifyPostDeleted(List<String> fcmTokens, Long postId, String title) {
        sendMulticast(fcmTokens, "지원했던 모집글이 삭제되었어요", title, "POST_DELETED", postId, "모집글 삭제 알림");
    }

    // 토큰 목록을 500개씩 나눠 multicast — 메시지 생성·발송 실패는 그 묶음만 건너뛰고 호출자에게 전파하지 않는다.
    // 응답에서 UNREGISTERED인 토큰은 모아 StaleFcmTokensEvent로 알린다(그 밖의 실패는 일시적일 수 있어 건드리지 않는다)
    private void sendMulticast(List<String> fcmTokens, String title, String body, String type, Long postId, String kind) {
        if (fcmTokens == null || fcmTokens.isEmpty()) {
            return;
        }
        List<String> staleTokens = new ArrayList<>();
        for (int from = 0; from < fcmTokens.size(); from += MULTICAST_LIMIT) {
            List<String> chunk = fcmTokens.subList(from, Math.min(from + MULTICAST_LIMIT, fcmTokens.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .addAllTokens(chunk)
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .putData("type", type)
                        .putData("postId", String.valueOf(postId))
                        .build();
                collectStaleTokens(chunk, firebaseMessaging.sendEachForMulticast(message), staleTokens);
            } catch (Exception e) {
                log.warn("{} 발송 실패 postId={}", kind, postId, e);
            }
        }
        if (!staleTokens.isEmpty()) {
            eventPublisher.publishEvent(new StaleFcmTokensEvent(staleTokens));
        }
    }

    // 응답은 요청 토큰 순서와 같다 — 실패 응답 중 UNREGISTERED(앱 삭제 등으로 죽은 토큰)만 골라 담는다
    private void collectStaleTokens(List<String> tokens, BatchResponse response, List<String> staleTokens) {
        if (response == null || response.getFailureCount() == 0) {
            return;
        }
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size() && i < tokens.size(); i++) {
            FirebaseMessagingException failure = responses.get(i).getException();
            if (failure != null && failure.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                staleTokens.add(tokens.get(i));
            }
        }
    }

    private void send(Message message, String kind, Long postId) {
        try {
            firebaseMessaging.send(message);
        } catch (Exception e) {
            log.warn("{} 발송 실패 postId={}", kind, postId, e);
        }
    }
}
