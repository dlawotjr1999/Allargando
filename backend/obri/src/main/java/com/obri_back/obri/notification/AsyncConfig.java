package com.obri_back.obri.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/*
 * 알림 발송 전용 비동기 실행기 설정
 * 스레드 2~4개·대기열 100개의 작은 풀 — 푸시는 best-effort라 대기열이 가득 차면 새 알림을 버리고 로그만 남긴다
 * (요청 스레드에서 직접 실행하면 FCM 지연이 다시 API 응답으로 돌아오므로 CallerRuns를 쓰지 않는다)
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("알림 발송 대기열이 가득 차 알림을 건너뜀"));
        // 종료 시 대기 중인 발송은 잠시 기다려 준다
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
