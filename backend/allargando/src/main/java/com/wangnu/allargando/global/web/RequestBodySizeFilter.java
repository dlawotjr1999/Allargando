package com.wangnu.allargando.global.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangnu.allargando.global.common.APIResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/*
 * /api/** 요청 본문의 전체 크기를 파싱 전에 제한하는 필터(기본 64KB, app.max-request-body-bytes, 0이면 끔).
 * DTO 검증(@Size 등)은 JSON을 객체로 만든 뒤에 돌기 때문에, 큰 본문을 보내는 것만으로 파싱·메모리 비용이 먼저 든다.
 *  - Content-Length가 상한을 넘으면 읽지 않고 바로 413으로 거절한다.
 *  - Content-Length가 없는 chunked 요청은 읽은 바이트를 세다가 상한을 넘으면 RequestBodyTooLargeException으로 끊고,
 *    GlobalExceptionHandler가 413으로 바꾼다.
 * 인증보다 먼저 돌아 가입 같은 비로그인 경로도 같은 상한을 받는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeFilter extends OncePerRequestFilter {

    static final String MESSAGE = "요청 본문이 너무 큽니다";

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestBodySizeFilter(@Value("${app.max-request-body-bytes:65536}") long maxBytes,
                                 ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return maxBytes <= 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), APIResponse.error(413, MESSAGE));
            return;
        }
        // 길이를 알 수 없는(chunked) 요청만 읽으면서 센다. 길이를 아는 요청은 위 검사로 이미 상한 이내다
        chain.doFilter(contentLength < 0 ? new CountingRequest(request, maxBytes) : request, response);
    }

    // 본문 입력 스트림을 읽은 바이트 수로 감시하는 요청 래퍼
    private static final class CountingRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream stream;

        CountingRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new CountingStream(super.getInputStream(), maxBytes);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    // 읽은 바이트가 상한을 넘으면 RequestBodyTooLargeException을 던지는 입력 스트림
    private static final class CountingStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long total;

        CountingStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            total += n;
            if (total > maxBytes) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
