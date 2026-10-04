package com.wangnu.allargando.global.exception;

/*
 * 중복 데이터 요청 시 발생
 * 409 Conflict
 * 예: 닉네임 중복, 중복 지원
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}