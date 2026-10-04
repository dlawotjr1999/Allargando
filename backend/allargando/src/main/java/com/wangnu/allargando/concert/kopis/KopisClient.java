package com.wangnu.allargando.concert.kopis;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/*
 * KOPIS(공연예술통합전산망) 오픈API에 대한 HTTP 접근만 담당(Jsoup 래핑). 파싱 로직은 여기 두지 않는다
 * KopisSyncService가 Mockito로 목킹할 수 있도록 별도 빈으로 분리
 * 응답이 XML이라 Jsoup을 HTML 파서가 아닌 XML 파서(Parser.xmlParser())로 사용
 */
@Component
public class KopisClient {

    // 서비스키가 쿼리스트링에 들어가므로 평문 http로 보내지 않는다. 호스트는 www 없는 kopis.or.kr — https://www.kopis.or.kr은
    // 301로 www 없는 쪽으로 넘어가는 것을 실제 호출로 확인했다. 테스트가 스텁 서버를 가리킬 수 있도록 프로퍼티로 재정의 가능
    static final String DEFAULT_BASE_URL = "https://kopis.or.kr";
    private static final String LIST_PATH_TEMPLATE =
            "/openApi/restful/pblprfr?service=%s&stdate=%s&eddate=%s&cpage=%d&rows=%d&shcate=%s";
    private static final int TIMEOUT_MS = 10_000;

    @Value("${kopis.service-key}")
    private String serviceKey;

    @Value("${kopis.base-url:" + DEFAULT_BASE_URL + "}")
    private String baseUrl;

    // 공연목록 조회. stdate/eddate는 "yyyyMMdd" 형식. genreCode는 KOPIS 장르코드(shcate) — 호출부(KopisSyncService)가
    // 장르별로 각각 호출해 합친다(KOPIS API가 shcate 다중값을 지원하지 않아 장르 하나당 별도 요청 필요)
    public Document fetchListDocument(String stdate, String eddate, int page, int rows, String genreCode) {
        String url = baseUrl + String.format(LIST_PATH_TEMPLATE, serviceKey, stdate, eddate, page, rows, genreCode);
        try {
            return Jsoup.connect(url).parser(Parser.xmlParser()).timeout(TIMEOUT_MS).get();
        } catch (IOException | IllegalArgumentException e) {
            throw failure(page, e);
        }
    }

    // 조회 실패 예외 — 원인 예외를 cause로 붙이지 않는다. Jsoup의 HttpStatusException·MalformedURL 메시지는 요청 URL을
    // 그대로 담고, URL 쿼리스트링에 서비스키가 있어 cause를 로그로 출력하면 키가 노출된다.
    // 그래서 메시지에는 상태코드나 예외 종류만 적고, 원인을 추적할 수 있게 스택트레이스만 복사한다(스택에는 URL이 없다)
    private KopisSyncException failure(int page, Exception cause) {
        String detail = cause instanceof HttpStatusException http
                ? "HTTP " + http.getStatusCode()
                : cause.getClass().getSimpleName();
        KopisSyncException exception = new KopisSyncException("KOPIS 공연목록 조회 실패: page=" + page + ", " + detail);
        exception.setStackTrace(cause.getStackTrace());
        return exception;
    }
}
