package com.wangnu.allargando.global.exception;

/*
 * 호출 빈도 제한을 넘었을 때 던진다(429). retryAfterSeconds는 응답의 Retry-After 헤더로 나간다.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
