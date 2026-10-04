package com.wangnu.allargando.global.common;

import lombok.Builder;
import lombok.Getter;

/**
 * API 공통 응답 형식
 * 모든 API 응답은 이 형식으로 반환
 * status: HTTP 상태 코드
 * message: 응답 메시지
 * data: 응답 데이터 (없으면 null)
 */
@Getter
@Builder
public class APIResponse<T> {

    private int status;
    private String message;
    private T data;

    /*
     * 성공 응답 생성 (데이터 포함)
     *
     * @param message 성공 메시지
     * @param data    응답 데이터
     * @return 200 응답
     */
    public static <T> APIResponse<T> ok(String message, T data) {
        return APIResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    /*
     * 성공 응답 생성 (데이터 없음)
     *
     * @param message 성공 메시지
     * @return 200 응답, data = null
     */
    public static <T> APIResponse<T> ok(String message) {
        return APIResponse.<T>builder()
                .status(200)
                .message(message)
                .data(null)
                .build();
    }

    /*
     * 실패 응답 생성
     *
     * @param status  HTTP 상태 코드
     * @param message 에러 메시지
     * @return 에러 응답
     */
    public static <T> APIResponse<T> error(int status, String message) {
        return APIResponse.<T>builder()
                .status(status)
                .message(message)
                .data(null)
                .build();
    }
}