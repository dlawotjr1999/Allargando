package com.obri_back.obri.concert.kopis;

import com.obri_back.obri.concert.kopis.dto.KopisPerformanceItem;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KopisResponseParserTest {

    // 실제 KOPIS 오픈API(shcate=CCCA) 응답 샘플 축약본(2건)
    private static final String LIST_XML = """
            <dbs>
            <db>
            <mt20id>PF300640</mt20id>
            <prfnm>고음악 오디세이, 음악의 언어: 극장 (Theatrum)</prfnm>
            <prfpdfrom>2026.12.11</prfpdfrom>
            <prfpdto>2026.12.11</prfpdto>
            <fcltynm>반포심산아트홀</fcltynm>
            <poster>http://www.kopis.or.kr/upload/pfmPoster/PF_PF300640_260910_151118.gif</poster>
            <area>서울특별시</area>
            <genrenm>서양음악(클래식)</genrenm>
            <openrun>N</openrun>
            <prfstate>공연예정</prfstate>
            </db>
            <db>
            <mt20id>PF300632</mt20id>
            <prfnm>제92회 코리아나 챔버 뮤직 소사이어티 정기연주회</prfnm>
            <prfpdfrom>2026.10.11</prfpdfrom>
            <prfpdto>2026.10.11</prfpdto>
            <fcltynm>예술의전당 [서울]</fcltynm>
            <poster>http://www.kopis.or.kr/upload/pfmPoster/PF_PF300632_260910_144546.jpg</poster>
            <area>서울특별시</area>
            <genrenm>서양음악(클래식)</genrenm>
            <openrun>N</openrun>
            <prfstate>공연예정</prfstate>
            </db>
            </dbs>
            """;

    private static final String EMPTY_XML = "<dbs></dbs>";

    private static final String MISSING_ID_XML = """
            <dbs>
            <db>
            <prfnm>식별자 없는 항목 — 스킵돼야 함</prfnm>
            </db>
            </dbs>
            """;

    private Document parseXml(String xml) {
        return Jsoup.parse(xml, "", Parser.xmlParser());
    }

    @Test
    void parse_extractsAllItemsWithFields() {
        List<KopisPerformanceItem> items = KopisResponseParser.parse(parseXml(LIST_XML));

        assertThat(items).hasSize(2);

        KopisPerformanceItem first = items.get(0);
        assertThat(first.externalId()).isEqualTo("PF300640");
        assertThat(first.title()).isEqualTo("고음악 오디세이, 음악의 언어: 극장 (Theatrum)");
        assertThat(first.category()).isEqualTo("서양음악(클래식)");
        assertThat(first.startDate()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(first.endDate()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(first.venue()).isEqualTo("반포심산아트홀");
        assertThat(first.region()).isEqualTo("서울특별시");
        assertThat(first.posterUrl()).isEqualTo("http://www.kopis.or.kr/upload/pfmPoster/PF_PF300640_260910_151118.gif");
        assertThat(first.url()).isEqualTo(
                "https://www.kopis.or.kr/por/db/pblprfr/pblprfrView.do?menuId=MNU_00020&mt20Id=PF300640");
    }

    @Test
    void parse_returnsEmptyListWhenNoDbElements() {
        List<KopisPerformanceItem> items = KopisResponseParser.parse(parseXml(EMPTY_XML));

        assertThat(items).isEmpty();
    }

    @Test
    void parse_skipsItemWithoutExternalId() {
        List<KopisPerformanceItem> items = KopisResponseParser.parse(parseXml(MISSING_ID_XML));

        assertThat(items).isEmpty();
    }

    // 날짜 형식이 어긋난 행(KOPIS가 형식을 바꾼 경우)은 그 행만 건너뛰고 나머지는 파싱한다 — 전체 동기화가 중단되지 않게
    @Test
    void parse_skipsRowWithMalformedDateAndKeepsOthers() {
        String xml = """
                <dbs>
                <db><mt20id>BAD</mt20id><prfnm>날짜 형식 오류</prfnm><prfpdfrom>2026-12-11</prfpdfrom><prfpdto>2026.12.11</prfpdto></db>
                <db><mt20id>OK</mt20id><prfnm>정상</prfnm><prfpdfrom>2026.12.11</prfpdfrom><prfpdto>2026.12.11</prfpdto></db>
                </dbs>
                """;

        List<KopisPerformanceItem> items = KopisResponseParser.parse(parseXml(xml));

        assertThat(items).extracting(KopisPerformanceItem::externalId).containsExactly("OK");
    }

    // 필수 태그가 비어도 파서는 null로 돌려준다(저장 단계의 NOT NULL 위반은 KopisSyncService가 행 단위로 격리)
    @Test
    void parse_returnsNullFieldsWhenOptionalTagsMissing() {
        String xml = "<dbs><db><mt20id>PF9</mt20id></db></dbs>";

        List<KopisPerformanceItem> items = KopisResponseParser.parse(parseXml(xml));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.externalId()).isEqualTo("PF9");
            assertThat(item.title()).isNull();
            assertThat(item.startDate()).isNull();
            assertThat(item.venue()).isNull();
        });
    }

    // KOPIS는 오류도 HTTP 200으로 준다(실제 응답) — 빈 목록(=정상 종료)으로 오해하지 않고 실패로 올린다
    @Test
    void parse_throwsWhenResponseHasErrorReturnCode() {
        String xml = """
                <dbs><db><returncode>02</returncode><errmsg>SERVICE KEY IS NOT REGISTERED ERROR</errmsg></db></dbs>
                """;

        assertThatThrownBy(() -> KopisResponseParser.parse(parseXml(xml)))
                .isInstanceOf(KopisSyncException.class)
                .hasMessageContaining("returncode=02")
                .hasMessageContaining("SERVICE KEY IS NOT REGISTERED ERROR");
    }

    // returncode 00은 정상 코드 — 오류로 취급하지 않는다
    @Test
    void parse_acceptsNormalReturnCode() {
        String xml = "<dbs><db><returncode>00</returncode></db></dbs>";

        assertThat(KopisResponseParser.parse(parseXml(xml))).isEmpty();
    }
}
