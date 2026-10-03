package com.obri_back.obri.concert.kopis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.obri_back.obri.global.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;

// 정기 실행이 수동 트리거와 겹치거나 실패해도 앱을 죽이지 않고, 겹침은 에러가 아닌 info로 남긴다(D16)
@ExtendWith(MockitoExtension.class)
class KopisSyncSchedulerTest {

    @Mock KopisSyncService kopisSyncService;
    @InjectMocks KopisSyncScheduler scheduler;

    private ListAppender<ILoggingEvent> attach() {
        Logger logger = (Logger) LoggerFactory.getLogger(KopisSyncScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(KopisSyncScheduler.class)).detachAppender(appender);
    }

    @Test
    void runScheduledSync_logsInfoNotErrorWhenAlreadyRunning() {
        doThrow(new ConflictException("이미 KOPIS 동기화가 진행 중입니다")).when(kopisSyncService).sync();
        ListAppender<ILoggingEvent> appender = attach();
        try {
            assertThatCode(scheduler::runScheduledSync).doesNotThrowAnyException();

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getThrowableProxy()).isNull(); // 스택트레이스 없음
            });
        } finally {
            detach(appender);
        }
    }

    @Test
    void runScheduledSync_logsErrorAndDoesNotThrowWhenSyncFails() {
        doThrow(new KopisSyncException("KOPIS 동기화 실패")).when(kopisSyncService).sync();
        ListAppender<ILoggingEvent> appender = attach();
        try {
            assertThatCode(scheduler::runScheduledSync).doesNotThrowAnyException();

            assertThat(appender.list).singleElement()
                    .extracting(ILoggingEvent::getLevel).isEqualTo(Level.ERROR);
        } finally {
            detach(appender);
        }
    }
}
