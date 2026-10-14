package com.wangnu.allargando.global.web;

import java.io.IOException;

/*
 * 본문을 읽는 도중 상한(app.max-request-body-bytes)을 넘었을 때 던지는 예외.
 * Content-Length 없이 chunked로 오는 요청은 헤더만으로 크기를 알 수 없어 읽은 바이트를 세다가 이 예외로 끊는다.
 * 입력 스트림 읽기가 던질 수 있는 예외는 IOException뿐이라 그 하위 타입으로 둔다.
 */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(long maxBytes) {
        super("요청 본문이 상한(" + maxBytes + "바이트)을 넘었습니다");
    }
}
