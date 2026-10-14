package com.wangnu.allargando.global.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangnu.allargando.global.exception.GlobalExceptionHandler;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * RequestBodySizeFilter 단위 테스트 — 실제 서블릿 컨테이너 없이 요청·응답 목으로 검증한다.
 * Content-Length 초과(헤더로 판단), chunked 초과(읽다가 끊김), 상한 이내·/api 밖·꺼짐 통과와
 * 읽기 중단이 GlobalExceptionHandler에서 413으로 바뀌는지를 본다.
 */
class RequestBodySizeFilterTest {

    private static final int LIMIT = 100;

    private final RequestBodySizeFilter filter = new RequestBodySizeFilter(LIMIT, new ObjectMapper());

    private MockHttpServletRequest post(String uri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    @Test
    void filter_rejectsWith413WhenContentLengthExceedsLimit() throws Exception {
        MockHttpServletRequest request = post("/api/posts", new byte[LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"status\":413").contains("요청 본문이 너무 큽니다");
        assertThat(chain.getRequest()).as("컨트롤러까지 가지 않는다").isNull();
    }

    @Test
    void filter_passesBodyWithinLimit() throws Exception {
        MockHttpServletRequest request = post("/api/posts", new byte[LIMIT]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void filter_cutsOffChunkedBodyOnceItExceedsLimit() throws Exception {
        // Content-Length를 모르는(chunked) 요청: 읽은 바이트가 상한을 넘는 순간 읽기가 끊긴다
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/posts") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[LIMIT * 3]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ServletInputStream stream = chain.getRequest().getInputStream();
        assertThatThrownBy(() -> drain(stream)).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void filter_doesNotTouchRequestsOutsideApi() throws Exception {
        MockHttpServletRequest request = post("/actuator/health", new byte[LIMIT * 10]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void filter_isDisabledWhenLimitIsZero() throws Exception {
        RequestBodySizeFilter disabled = new RequestBodySizeFilter(0, new ObjectMapper());
        MockHttpServletRequest request = post("/api/posts", new byte[LIMIT * 10]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        disabled.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void handler_mapsInterruptedBodyReadTo413() {
        // Jackson이 입력 스트림의 IOException을 HttpMessageNotReadableException으로 감싸 올리는 경로
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "읽기 실패", new RequestBodyTooLargeException(LIMIT), null);

        ResponseEntity<?> response = new GlobalExceptionHandler().handleHttpMessageNotReadableException(e);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void handler_keepsMalformedJsonAs400() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
                "형식 오류", new IOException("Unexpected character"), null);

        ResponseEntity<?> response = new GlobalExceptionHandler().handleHttpMessageNotReadableException(e);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buffer = new byte[16];
        while (in.read(buffer) >= 0) {
            // 끝까지 읽는다
        }
    }
}
