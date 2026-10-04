package com.wangnu.allargando.global.exception;

/*
 * 인증 실패 시 발생
 * 401 Unauthorized
 * 예: Firebase ID Token 검증 실패
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
