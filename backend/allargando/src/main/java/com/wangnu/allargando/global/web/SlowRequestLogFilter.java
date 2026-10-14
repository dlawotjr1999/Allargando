package com.wangnu.allargando.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/*
 * 처리에 오래 걸린 /api/** 요청을 WARN으로 남기는 필터(기본 1000ms 이상, app.slow-request-ms, 0이면 끔).
 * 느린 SQL은 Hibernate 느린 쿼리 로그(SLOW_QUERY_LOG_MS)가 알려 주지만, 쿼리 밖의 시간(외부 호출·직렬화·대기)과
 * 어느 엔드포인트가 느린지는 이 로그로만 알 수 있다. 쿼리 문자열은 개인정보가 섞일 수 있어 남기지 않고 경로만 남긴다.
 * 인증·호출 제한까지 포함한 전체 시간을 재도록 가장 바깥(본문 크기 필터 바로 뒤)에서 돈다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SlowRequestLogFilter extends OncePerRequestFilter {

    private final long thresholdMs;

    public SlowRequestLogFilter(@Value("${app.slow-request-ms:1000}") long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return thresholdMs <= 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (elapsedMs >= thresholdMs) {
                log.warn("[느린 요청] {} {} {}ms status={}", request.getMethod(), request.getRequestURI(),
                        elapsedMs, response.getStatus());
            }
        }
    }
}
