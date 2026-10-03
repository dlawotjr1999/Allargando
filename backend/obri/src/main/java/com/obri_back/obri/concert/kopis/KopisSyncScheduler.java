package com.obri_back.obri.concert.kopis;

import com.obri_back.obri.global.exception.ConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * KOPIS 동기화 스케줄러 — 매일 새벽 3시 30분 자동 실행(기본값). kopis.sync.cron으로 재정의 가능(로컬 검증 시 짧게 설정)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KopisSyncScheduler {

    private final KopisSyncService kopisSyncService;

    // 서버(컨테이너) 기본 시간대가 UTC여도 한국 시각 기준으로 돌도록 zone을 고정
    @Scheduled(cron = "${kopis.sync.cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void runScheduledSync() {
        try {
            kopisSyncService.sync();
        } catch (ConflictException e) {
            // 수동 트리거와 겹친 정상적인 상황(D16) — 에러가 아니라 건너뛴 사실만 남긴다
            log.info("KOPIS 정기 동기화 건너뜀 — 이미 실행 중");
        } catch (Exception e) {
            // 스케줄 실행 실패가 앱을 죽이지 않도록 격리하고 로그로만 남김
            log.error("KOPIS 정기 동기화 실패", e);
        }
    }
}
