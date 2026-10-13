package com.wangnu.allargando.global.exception;

/*
 * 외부 서비스(Firebase 등)에 닿지 못해 요청을 처리할 수 없을 때 던진다(503).
 * 요청이 잘못된 것이 아니므로 401·400과 구분한다 — 앱은 401을 받으면 로그아웃시키기 때문이다.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
