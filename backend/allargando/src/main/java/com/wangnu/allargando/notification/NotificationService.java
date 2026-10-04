package com.wangnu.allargando.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * FCM 알림 발송 서비스
 * - 신규 모집글: "new_post" 토픽 broadcast
 * - 지원 결과(수락/거절): fcm_token 단건 push
 * - 모집글 수정: PENDING·ACCEPTED 지원자 multicast
 * - 모집글 삭제: ACCEPTED 지원자 multicast
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

    private final FirebaseMessaging firebaseMessaging;

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
        if (fcmTokens == null || fcmTokens.isEmpty()) {
            return;
        }
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(fcmTokens)
                .setNotification(Notification.builder()
                        .setTitle("지원한 모집글이 수정되었어요")
                        .setBody(title)
                        .build())
                .putData("type", "POST_UPDATED")
                .putData("postId", String.valueOf(postId))
                .build();
        try {
            firebaseMessaging.sendEachForMulticast(message);
        } catch (Exception e) {
            log.warn("모집글 수정 알림 발송 실패 postId={}", postId, e);
        }
    }

    // 모집글 삭제 → 확정(ACCEPTED)된 지원자에게 multicast
    public void notifyPostDeleted(List<String> fcmTokens, Long postId, String title) {
        if (fcmTokens == null || fcmTokens.isEmpty()) {
            return;
        }
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(fcmTokens)
                .setNotification(Notification.builder()
                        .setTitle("지원했던 모집글이 삭제되었어요")
                        .setBody(title)
                        .build())
                .putData("type", "POST_DELETED")
                .putData("postId", String.valueOf(postId))
                .build();
        try {
            firebaseMessaging.sendEachForMulticast(message);
        } catch (Exception e) {
            log.warn("모집글 삭제 알림 발송 실패 postId={}", postId, e);
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
