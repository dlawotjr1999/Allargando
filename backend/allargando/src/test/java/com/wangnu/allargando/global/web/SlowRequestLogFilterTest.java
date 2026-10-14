package com.wangnu.allargando.global.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * SlowRequestLogFilter 단위 테스트 — 임계값 이상이면 WARN이 남고, 미만·/api 밖·꺼짐이면 남지 않으며,
 * 쿼리 문자열은 로그에 들어가지 않고, 요청이 예외로 끝나도 로그가 남은 채 예외가 그대로 전달되는지 본다.
 */
class SlowRequestLogFilterTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SlowRequestLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    // 필터 체인 람다는 검사 예외를 던질 수 없어 대기를 감싼다. 임계값(1ms)보다 충분히 오래 기다린다
    private static void pause() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> logged() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void filter_logsWarnWhenRequestIsAtOrOverThreshold() throws Exception {
        SlowRequestLogFilter filter = new SlowRequestLogFilter(1);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.setQueryString("keyword=secret");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> pause());

        assertThat(logged()).hasSize(1);
        assertThat(logged().get(0)).contains("[느린 요청]", "GET", "/api/posts").doesNotContain("secret");
    }

    @Test
    void filter_staysSilentWhenRequestIsFast() throws Exception {
        SlowRequestLogFilter filter = new SlowRequestLogFilter(60_000);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/posts"), new MockHttpServletResponse(),
                (req, res) -> { });

        assertThat(logged()).isEmpty();
    }

    @Test
    void filter_ignoresRequestsOutsideApiAndWhenDisabled() throws Exception {
        new SlowRequestLogFilter(1).doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(), (req, res) -> pause());
        new SlowRequestLogFilter(0).doFilter(new MockHttpServletRequest("GET", "/api/posts"),
                new MockHttpServletResponse(), (req, res) -> pause());

        assertThat(logged()).isEmpty();
    }

    @Test
    void filter_logsAndRethrowsWhenRequestFails() {
        SlowRequestLogFilter filter = new SlowRequestLogFilter(1);

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("POST", "/api/posts"),
                new MockHttpServletResponse(), (req, res) -> {
                    pause();
                    throw new IllegalStateException("boom");
                })).isInstanceOf(IllegalStateException.class);

        assertThat(logged()).hasSize(1);
    }
}
