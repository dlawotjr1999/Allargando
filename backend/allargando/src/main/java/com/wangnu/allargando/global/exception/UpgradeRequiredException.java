package com.wangnu.allargando.global.exception;

/*
 * 앱 버전이 서버가 지원하는 최소 버전보다 낮을 때 던진다(426).
 * 앱은 이 응답을 받으면 스토어로 보내는 업데이트 안내 화면을 띄운다.
 */
public class UpgradeRequiredException extends RuntimeException {
    public UpgradeRequiredException(String message) {
        super(message);
    }
}
