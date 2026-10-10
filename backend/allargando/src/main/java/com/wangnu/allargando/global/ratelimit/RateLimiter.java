package com.wangnu.allargando.global.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 고정 시간 창(fixed window) 방식의 요청 횟수 계산기 — 키(예: "PostController#create|u:7")마다
 * 창이 시작된 뒤 몇 번 호출됐는지를 메모리에 센다.
 *
 * 서버 1대 전제(CLAUDE.md §3.10)라 메모리에 둔다. 서버가 늘면 인스턴스마다 따로 세므로
 * 한도가 인스턴스 수만큼 느슨해진다 — 그때 공유 저장소(Redis 등)로 옮긴다. 재시작하면 카운터가 초기화된다.
 * 창 경계에서 최대 한도의 2배까지 몰릴 수 있지만 남용 방지가 목적이라 그 정도 오차는 감수한다.
 * IP를 바꿔 가며 보내는 대량 공격은 이 계층이 아니라 서버 앞단(ALB·Nginx·WAF)에서 막는다.
 */
@Component
public class RateLimiter {

    // 이 개수를 넘으면 만료된 키를 지운다(키가 무한히 쌓이지 않게)
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    // 테스트에서 시각을 고정하려고 둔 생성자
    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /*
     * 호출을 한 번 센다.
     * param  : key 제한 대상(엔드포인트 + 사용자·IP)
     * param  : limit 창 안의 최대 허용 횟수
     * param  : windowSeconds 창 길이(초)
     * return : 허용되면 0, 막히면 창이 끝나기까지 남은 초(1 이상) — Retry-After에 쓴다
     */
    public long tryAcquire(String key, int limit, long windowSeconds) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().resetAtMillis <= now);
        }
        long[] retryAfter = {0};
        windows.compute(key, (k, window) -> {
            if (window == null || window.resetAtMillis <= now) {
                return new Window(now + windowSeconds * 1000L);
            }
            if (window.count >= limit) {
                retryAfter[0] = Math.max(1L, (window.resetAtMillis - now + 999L) / 1000L);
                return window;
            }
            window.count++;
            return window;
        });
        return retryAfter[0];
    }

    // 키 하나의 현재 창. count는 compute 안에서만 바뀌므로 키 단위로 직렬화된다
    private static final class Window {
        private final long resetAtMillis;
        private int count = 1;

        private Window(long resetAtMillis) {
            this.resetAtMillis = resetAtMillis;
        }
    }
}
