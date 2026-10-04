package com.wangnu.allargando.concert.kopis;

import com.wangnu.allargando.concert.kopis.dto.KopisPerformanceItem;

import lombok.extern.slf4j.Slf4j;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/*
 * KOPIS 공연목록 XML(<dbs><db>...</db></dbs>) 파싱 — 순수 파싱 로직만 담당(네트워크 없음)이라
 * 네트워크 없이 유닛테스트 가능
 */
@Slf4j
public class KopisResponseParser {

    private static final DateTimeFormatter KOPIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    // KOPIS 오픈API 응답엔 상세 페이지 링크가 없어 mt20id로 직접 조립
    private static final String DETAIL_URL_TEMPLATE =
            "https://www.kopis.or.kr/por/db/pblprfr/pblprfrView.do?menuId=MNU_00020&mt20Id=%s";
    // KOPIS 오류 응답의 returncode가 아닌 정상 코드 — 정상 목록 응답에는 returncode 자체가 없다(실제 호출로 확인)
    private static final String RETURN_CODE_OK = "00";

    /*
     * 목록 응답 문서 → 항목 목록. <db> 태그가 하나도 없으면(빈 <dbs/>) 빈 리스트 반환 — 마지막 페이지 판단 기준
     * KOPIS는 오류(잘못된 서비스키 등)도 HTTP 200 + <db><returncode>…</returncode><errmsg>…</errmsg></db>로 주므로,
     * returncode가 있으면서 정상(00)이 아니면 빈 목록(=정상 종료)으로 오해하지 않도록 KopisSyncException을 던진다.
     * 날짜 형식이 어긋난 행은 그 행만 건너뛰고 나머지는 계속 파싱한다(한 행 때문에 전체 동기화가 중단되지 않게)
     */
    public static List<KopisPerformanceItem> parse(Document document) {
        throwIfErrorResponse(document);

        Elements rows = document.select("db");
        List<KopisPerformanceItem> items = new ArrayList<>();

        for (Element row : rows) {
            String externalId = text(row, "mt20id");
            if (externalId == null || externalId.isEmpty()) {
                continue; // 식별자 없는 행은 upsert 기준이 없어 스킵
            }

            try {
                items.add(new KopisPerformanceItem(
                        externalId,
                        text(row, "prfnm"),
                        text(row, "genrenm"),
                        parseDate(text(row, "prfpdfrom")),
                        parseDate(text(row, "prfpdto")),
                        text(row, "fcltynm"),
                        text(row, "area"),
                        text(row, "poster"),
                        String.format(DETAIL_URL_TEMPLATE, externalId)
                ));
            } catch (DateTimeParseException e) {
                log.warn("KOPIS 행 건너뜀(날짜 형식 오류): mt20id={}", externalId);
            }
        }

        return items;
    }

    // returncode가 있고 00이 아니면 오류 응답 — 잘못된 키·만료·한도 초과가 "신규 0건 성공"으로 보이지 않게 한다
    private static void throwIfErrorResponse(Document document) {
        Element returnCode = document.selectFirst("returncode");
        if (returnCode == null) {
            return;
        }
        String code = returnCode.text().trim();
        if (RETURN_CODE_OK.equals(code)) {
            return;
        }
        String errorMessage = text(document, "errmsg");
        throw new KopisSyncException("KOPIS 오류 응답: returncode=" + code
                + (errorMessage == null ? "" : ", errmsg=" + errorMessage));
    }

    private static String text(Element row, String tag) {
        Element el = row.selectFirst(tag);
        return el != null ? el.text().trim() : null;
    }

    private static LocalDate parseDate(String raw) {
        return raw == null || raw.isEmpty() ? null : LocalDate.parse(raw, KOPIS_DATE_FORMAT);
    }
}
