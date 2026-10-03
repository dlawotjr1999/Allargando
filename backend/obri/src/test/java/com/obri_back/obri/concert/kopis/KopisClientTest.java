package com.obri_back.obri.concert.kopis;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.obri_back.obri.concert.repository.ConcertRepository;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// 실제 HTTP(JDK 내장 스텁 서버)로 KopisClient를 검증한다: 서비스키가 요청에는 실리지만 실패 예외·로그에는 새지 않아야 한다
class KopisClientTest {

    private static final String SECRET_KEY = "SECRET-SERVICE-KEY-1234567890";

    private HttpServer server;
    private final AtomicReference<String> requestedUri = new AtomicReference<>();
    private KopisClient client;

    // 서버를 시작한다. status와 body로 응답하고, 요청된 URI(쿼리 포함)를 기록한다
    private void startServer(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedUri.set(exchange.getRequestURI().toString());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new KopisClient();
        ReflectionTestUtils.setField(client, "serviceKey", SECRET_KEY);
        ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        requestedUri.set(null);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // 평문 http로 서비스키를 보내지 않는다 — 운영 기본 주소는 https, www 호스트(301)는 쓰지 않는다
    @Test
    void defaultBaseUrl_isHttpsWithoutWwwHost() {
        assertThat(KopisClient.DEFAULT_BASE_URL).isEqualTo("https://kopis.or.kr");
    }

    @Test
    void fetchListDocument_sendsRequestWithKeyAndParams() throws Exception {
        startServer(200, "<dbs/>");

        Document document = client.fetchListDocument("20261003", "20270403", 2, 100, "CCCA");

        assertThat(document.select("db")).isEmpty();
        assertThat(requestedUri.get()).startsWith("/openApi/restful/pblprfr?")
                .contains("service=" + SECRET_KEY, "stdate=20261003", "eddate=20270403",
                        "cpage=2", "rows=100", "shcate=CCCA");
    }

    // 4xx/5xx 응답: Jsoup HttpStatusException의 메시지는 키가 든 URL을 담으므로 cause로 붙이지 않는다
    @Test
    void fetchListDocument_failureMessageHasStatusButNoKeyNorCause() throws Exception {
        startServer(500, "error");

        assertThatThrownBy(() -> client.fetchListDocument("20261003", "20270403", 3, 100, "CCCA"))
                .isInstanceOfSatisfying(KopisSyncException.class, e -> {
                    assertThat(e.getMessage()).contains("page=3").contains("HTTP 500").doesNotContain(SECRET_KEY);
                    assertThat(e.getCause()).isNull();
                });
        assertThat(requestedUri.get()).contains(SECRET_KEY); // 대조군: 요청에는 키가 실려 갔다
    }

    // 연결 자체가 실패해도 예외 종류만 남고 키는 없다
    @Test
    void fetchListDocument_connectionFailureHasNoKey() throws Exception {
        startServer(200, "<dbs/>");
        server.stop(0); // 포트를 닫아 연결 실패를 만든다

        assertThatThrownBy(() -> client.fetchListDocument("20261003", "20270403", 1, 100, "CCCA"))
                .isInstanceOfSatisfying(KopisSyncException.class,
                        e -> assertThat(e.getMessage()).doesNotContain(SECRET_KEY));
    }

    // 동기화 전체 경로의 로그(메시지·스택트레이스·cause 체인)에 서비스키가 한 글자도 남지 않는다
    @Test
    void sync_neverLogsServiceKeyWhenKopisReturnsServerError() throws Exception {
        startServer(500, "error");
        KopisSyncService service = new KopisSyncService(client, mock(ConcertRepository.class));
        Logger logger = (Logger) LoggerFactory.getLogger(KopisSyncService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(service::sync).isInstanceOf(KopisSyncException.class);

            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(SECRET_KEY);
                if (event.getThrowableProxy() != null) {
                    assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy())).doesNotContain(SECRET_KEY);
                }
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
