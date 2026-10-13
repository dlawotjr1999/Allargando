package com.wangnu.allargando.global.ratelimit;

import com.wangnu.allargando.global.exception.TooManyRequestsException;
import com.wangnu.allargando.user.entity.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/*
 * 컨트롤러에 닿기 전에 호출 빈도를 확인하는 인터셉터.
 *   1) 모든 /api/** 요청: 사용자(비로그인은 IP)당 분당 120회 — 계정·IP 하나가 서버를 독점하지 못하게 하는 기본 한도
 *   2) @RateLimit이 붙은 메서드: 그 메서드의 한도를 추가로 확인
 * 한도를 넘으면 TooManyRequestsException을 던지고 GlobalExceptionHandler가 429 + Retry-After로 바꾼다.
 *
 * RateLimiter 빈이 없는 컨텍스트(@WebMvcTest)에서는 아무것도 하지 않는다 — 컨트롤러 테스트가 호출 횟수에 영향받지 않게 한다.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    static final int BASELINE_LIMIT = 120;
    static final long BASELINE_WINDOW_SECONDS = 60;

    private static final String MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요";

    private final ObjectProvider<RateLimiter> limiterProvider;

    public RateLimitInterceptor(ObjectProvider<RateLimiter> limiterProvider) {
        this.limiterProvider = limiterProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RateLimiter limiter = limiterProvider.getIfAvailable();
        if (limiter == null || !(handler instanceof HandlerMethod method)) {
            return true;
        }

        String subject = subject(request);
        check(limiter, "all|" + subject, BASELINE_LIMIT, BASELINE_WINDOW_SECONDS);

        RateLimit annotation = method.getMethodAnnotation(RateLimit.class);
        if (annotation != null) {
            String target = annotation.by() == RateLimit.By.IP ? "ip:" + request.getRemoteAddr() : subject;
            String bucket = method.getBeanType().getSimpleName() + "#" + method.getMethod().getName();
            check(limiter, bucket + "|" + target, annotation.limit(), annotation.windowSeconds());
        }
        return true;
    }

    // 로그인한 사용자는 id로, 아니면 IP로 구분한다
    private String subject(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user && user.getId() != null) {
            return "u:" + user.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void check(RateLimiter limiter, String key, int limit, long windowSeconds) {
        long retryAfter = limiter.tryAcquire(key, limit, windowSeconds);
        if (retryAfter > 0) {
            throw new TooManyRequestsException(MESSAGE, retryAfter);
        }
    }
}
