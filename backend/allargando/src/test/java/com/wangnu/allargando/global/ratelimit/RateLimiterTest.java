package com.wangnu.allargando.global.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// 고정 시간 창 계산기: 한도까지 허용, 넘으면 남은 초를 돌려주고, 창이 끝나면 다시 허용한다
class RateLimiterTest {

    // 시각을 직접 옮길 수 있는 시계
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-10T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void tryAcquire_allowsUpToLimitThenBlocksWithRetryAfter() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);

        assertThat(limiter.tryAcquire("k", 3, 60)).isZero();
        assertThat(limiter.tryAcquire("k", 3, 60)).isZero();
        assertThat(limiter.tryAcquire("k", 3, 60)).isZero();

        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryAcquire("k", 3, 60)).isEqualTo(40);
    }

    @Test
    void tryAcquire_allowsAgainAfterWindowEnds() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        limiter.tryAcquire("k", 1, 60);
        assertThat(limiter.tryAcquire("k", 1, 60)).isPositive();

        clock.advance(Duration.ofSeconds(60));

        assertThat(limiter.tryAcquire("k", 1, 60)).isZero();
    }

    @Test
    void tryAcquire_countsEachKeySeparately() {
        RateLimiter limiter = new RateLimiter(new MutableClock());
        limiter.tryAcquire("a", 1, 60);

        assertThat(limiter.tryAcquire("a", 1, 60)).isPositive();
        assertThat(limiter.tryAcquire("b", 1, 60)).isZero();
    }

    @Test
    void tryAcquire_doesNotCountBlockedCallsTowardsNextWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        limiter.tryAcquire("k", 1, 60);
        for (int i = 0; i < 50; i++) {
            limiter.tryAcquire("k", 1, 60); // 막힌 호출은 창을 연장하지 않는다
        }

        clock.advance(Duration.ofSeconds(60));

        assertThat(limiter.tryAcquire("k", 1, 60)).isZero();
    }
}
