package com.wangnu.allargando.global.exception;

/*
 * 존재하지 않는 리소스 요청 시 발생
 * 404 Not Found
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}