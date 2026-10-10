package com.wangnu.allargando.global.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * 컨트롤러 메서드의 호출 빈도 제한 — 기준(window) 안에서 limit번을 넘으면 429로 응답한다.
 * 세는 일은 RateLimitInterceptor가 컨트롤러에 닿기 전에 한다(요청 본문을 읽기 전).
 * 애너테이션이 없는 엔드포인트도 인터셉터의 기본 한도(사용자·IP당 분당 120회)는 적용된다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    // 기준 시간(windowSeconds) 안에 허용하는 최대 호출 수
    int limit();

    // 기준 시간(초)
    long windowSeconds();

    // 누구 단위로 세는가. 로그인이 필요 없는 엔드포인트(가입, 닉네임 확인)는 IP를 쓴다
    By by() default By.USER;

    enum By {
        // 로그인한 사용자 id 기준(로그인하지 않았으면 IP로 센다)
        USER,
        // 요청 IP 기준 — 프록시 뒤에서는 server.forward-headers-strategy=native가 있어야 실제 클라이언트 IP가 된다
        IP
    }
}
