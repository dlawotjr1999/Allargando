package com.wangnu.allargando.notification;

import com.wangnu.allargando.notification.event.ApplicationResultNotificationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/*
 * NotificationEventListener가 실제로 커밋 이후에만 발송하고,
 * 롤백되면 아예 발송하지 않는지 — @TransactionalEventListener 배선 자체를 검증
 * (단위 테스트만으로는 이 타이밍 보장을 확인할 수 없어 실제 트랜잭션이 필요)
 */
@SpringBootTest
class NotificationAfterCommitTest {

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    NotificationService notificationService;

    @Test
    void event_isDeliveredAfterCommit_notDuringTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            eventPublisher.publishEvent(new ApplicationResultNotificationEvent("token-commit", true));
            // 트랜잭션이 아직 커밋되지 않았으므로 이 시점엔 발송되지 않아야 함
            verifyNoInteractions(notificationService);
            return null;
        });

        // 커밋 완료 후에는 발송됐어야 함 — 발송은 비동기라 완료를 잠시 기다린다
        verify(notificationService, timeout(3000).times(1)).notifyResult("token-commit", true);
    }

    @Test
    void event_isNotDeliveredWhenTransactionRollsBack() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            eventPublisher.publishEvent(new ApplicationResultNotificationEvent("token-rollback", true));
            status.setRollbackOnly();
            return null;
        });

        // 롤백됐으므로 이벤트 자체가 버려져 발송이 아예 없어야 함(유령 알림 방지 — #15의 핵심)
        // 비동기 발송이 늦게 도는 경우까지 보려고 잠시 기다린 뒤에도 호출이 없어야 한다
        verify(notificationService, after(500).never()).notifyResult("token-rollback", true);
    }

    // 발송은 요청(커밋한) 스레드가 아니라 알림 전용 스레드에서 돈다 — FCM이 느려도 API 응답·DB 커넥션을 붙잡지 않는다
    @Test
    void event_isDeliveredOnNotificationThread_notCallerThread() {
        java.util.concurrent.atomic.AtomicReference<String> deliveredOn = new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(inv -> {
            deliveredOn.set(Thread.currentThread().getName());
            return null;
        }).when(notificationService).notifyResult("token-thread", true);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            eventPublisher.publishEvent(new ApplicationResultNotificationEvent("token-thread", true));
            return null;
        });

        verify(notificationService, timeout(3000)).notifyResult("token-thread", true);
        org.assertj.core.api.Assertions.assertThat(deliveredOn.get())
                .startsWith("notification-")
                .isNotEqualTo(Thread.currentThread().getName());
    }
}
