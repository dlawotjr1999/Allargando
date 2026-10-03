package com.obri_back.obri.concert.kopis;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.obri_back.obri.concert.entity.Concert;
import com.obri_back.obri.concert.repository.ConcertRepository;
import com.obri_back.obri.global.exception.ConflictException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KopisSyncServiceTest {

    @Mock KopisClient client;
    @Mock ConcertRepository concertRepository;
    @InjectMocks KopisSyncService kopisSyncService;

    private static final String ONE_ITEM_XML = """
            <dbs>
            <db>
            <mt20id>PF1</mt20id>
            <prfnm>신규 연주회</prfnm>
            <prfpdfrom>2026.12.11</prfpdfrom>
            <prfpdto>2026.12.11</prfpdto>
            <fcltynm>어느 콘서트홀</fcltynm>
            <poster>http://example.com/poster.gif</poster>
            <area>서울특별시</area>
            <genrenm>서양음악(클래식)</genrenm>
            </db>
            </dbs>
            """;

    private static final String EMPTY_XML = "<dbs></dbs>";

    private Document parseXml(String xml) {
        return Jsoup.parse(xml, "", Parser.xmlParser());
    }

    private Concert existingConcert() {
        return Concert.fromSync("PF1", "신규 연주회", "서양음악(클래식)",
                LocalDate.of(2026, 12, 11), LocalDate.of(2026, 12, 11),
                "어느 콘서트홀", "서울특별시", "http://example.com/poster.gif",
                "https://www.kopis.or.kr/por/db/pblprfr/pblprfrView.do?menuId=MNU_00020&mt20Id=PF1");
    }

    // CCCA(서양음악/클래식)에만 항목이 있고, CCCC(국악)·CCCD(대중음악)는 1페이지부터 비어있는 전형적인 상황을 재현
    private void stubOnlyClassicalGenreHasItem() {
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCA")))
                .thenReturn(parseXml(ONE_ITEM_XML));
        when(client.fetchListDocument(anyString(), anyString(), eq(2), eq(100), eq("CCCA")))
                .thenReturn(parseXml(EMPTY_XML));
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC")))
                .thenReturn(parseXml(EMPTY_XML));
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD")))
                .thenReturn(parseXml(EMPTY_XML));
    }

    @Test
    void sync_savesNewItemAndStopsAtEmptyPagePerGenre() {
        stubOnlyClassicalGenreHasItem();
        when(concertRepository.findByExternalId("PF1")).thenReturn(Optional.empty());

        int savedCount = kopisSyncService.sync();

        assertThat(savedCount).isEqualTo(1);
        verify(concertRepository, times(1)).save(any(Concert.class));
        verify(client, never()).fetchListDocument(anyString(), anyString(), eq(3), eq(100), eq("CCCA"));
        verify(client, never()).fetchListDocument(anyString(), anyString(), eq(2), eq(100), eq("CCCC"));
        verify(client, never()).fetchListDocument(anyString(), anyString(), eq(2), eq(100), eq("CCCD"));
    }

    @Test
    void sync_updatesExistingItemWithoutCountingAsNew() {
        Concert existing = existingConcert();
        stubOnlyClassicalGenreHasItem();
        when(concertRepository.findByExternalId("PF1")).thenReturn(Optional.of(existing));

        int savedCount = kopisSyncService.sync();

        assertThat(savedCount).isZero(); // 갱신이지 신규 저장이 아니므로 신규 건수는 0
        verify(concertRepository, times(1)).save(existing);
    }

    @Test
    void sync_returnsZeroWhenEveryGenreFirstPageIsEmpty() {
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), anyString()))
                .thenReturn(parseXml(EMPTY_XML));

        int savedCount = kopisSyncService.sync();

        assertThat(savedCount).isZero();
        verifyNoInteractions(concertRepository);
        verify(client, never()).fetchListDocument(anyString(), anyString(), eq(2), eq(100), anyString());
    }

    private static String db(String id, String from) {
        return "<db><mt20id>" + id + "</mt20id><prfnm>공연 " + id + "</prfnm><prfpdfrom>" + from
                + "</prfpdfrom><prfpdto>2026.12.11</prfpdto><fcltynm>홀</fcltynm><area>서울특별시</area>"
                + "<genrenm>서양음악(클래식)</genrenm></db>";
    }

    private static String dbs(String... rows) {
        return "<dbs>" + String.join("", rows) + "</dbs>";
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(KopisSyncService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detach(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(KopisSyncService.class)).detachAppender(appender);
    }

    // 전 장르 조회 실패는 "신규 0건 성공"이 아니라 실패로 드러난다(과거: 0을 반환해 정상 종료로 보였음)
    @Test
    void sync_throwsWhenEveryGenreFails() {
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), anyString()))
                .thenThrow(new KopisSyncException("네트워크 오류"));

        assertThatThrownBy(() -> kopisSyncService.sync())
                .isInstanceOf(KopisSyncException.class)
                .hasMessageContaining("모든 장르");

        verifyNoInteractions(concertRepository);
        // 장르 하나가 실패해도 나머지 장르는 시도한다: CCCA/CCCC/CCCD 각각 1페이지
        verify(client, times(3)).fetchListDocument(anyString(), anyString(), eq(1), eq(100), anyString());
    }

    // 잘못된 서비스키처럼 KOPIS가 HTTP 200 + returncode 오류로 응답해도 실패로 드러난다
    @Test
    void sync_throwsWhenKopisReturnsErrorResponseForEveryGenre() {
        when(client.fetchListDocument(anyString(), anyString(), anyInt(), eq(100), anyString()))
                .thenReturn(parseXml("<dbs><db><returncode>02</returncode><errmsg>SERVICE KEY IS NOT REGISTERED ERROR</errmsg></db></dbs>"));

        assertThatThrownBy(() -> kopisSyncService.sync())
                .isInstanceOf(KopisSyncException.class)
                .hasMessageContaining("모든 장르");
    }

    // 일부 장르만 실패하면 나머지 결과는 반영하고 정상 반환하되 실패 장르를 경고로 남긴다
    @Test
    void sync_returnsCountAndWarnsWhenOnlySomeGenresFail() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        try {
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCA")))
                    .thenReturn(parseXml(ONE_ITEM_XML));
            when(client.fetchListDocument(anyString(), anyString(), eq(2), eq(100), eq("CCCA")))
                    .thenReturn(parseXml(EMPTY_XML));
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC")))
                    .thenThrow(new KopisSyncException("장애"));
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD")))
                    .thenReturn(parseXml(EMPTY_XML));
            when(concertRepository.findByExternalId("PF1")).thenReturn(Optional.empty());

            int savedCount = kopisSyncService.sync();

            assertThat(savedCount).isEqualTo(1);
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.contains("일부 실패") && m.contains("CCCC"));
        } finally {
            detach(appender);
        }
    }

    // 중간 페이지가 실패하면 이미 저장한 페이지는 유지하고 그 장르만 중단, 다른 장르는 계속한다
    @Test
    void sync_keepsSavedPagesAndStopsOnlyThatGenreWhenMiddlePageFails() {
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCA")))
                .thenReturn(parseXml(ONE_ITEM_XML));
        when(client.fetchListDocument(anyString(), anyString(), eq(2), eq(100), eq("CCCA")))
                .thenThrow(new KopisSyncException("중간 페이지 실패"));
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC")))
                .thenReturn(parseXml(EMPTY_XML));
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD")))
                .thenReturn(parseXml(EMPTY_XML));
        when(concertRepository.findByExternalId("PF1")).thenReturn(Optional.empty());

        int savedCount = kopisSyncService.sync();

        assertThat(savedCount).isEqualTo(1);
        verify(client, never()).fetchListDocument(anyString(), anyString(), eq(3), eq(100), eq("CCCA"));
        verify(client).fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC"));
        verify(client).fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD"));
    }

    // 저장이 실패한 불량 행(NOT NULL·길이 초과)은 그 행만 건너뛰고 나머지 행·장르를 계속한다.
    // 로그에는 원인 요약 첫 줄만 남고 행 값이 든 둘째 줄 이후는 남지 않는다
    @Test
    void sync_skipsRowThatFailsToSaveAndContinues() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        try {
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCA")))
                    .thenReturn(parseXml(dbs(db("BAD", "2026.12.11"), db("PF2", "2026.12.11"))));
            when(client.fetchListDocument(anyString(), anyString(), eq(2), eq(100), eq("CCCA")))
                    .thenReturn(parseXml(EMPTY_XML));
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC")))
                    .thenReturn(parseXml(EMPTY_XML));
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD")))
                    .thenReturn(parseXml(EMPTY_XML));
            when(concertRepository.findByExternalId(anyString())).thenReturn(Optional.empty());
            when(concertRepository.save(argThat(c -> c != null && "BAD".equals(c.getExternalId()))))
                    .thenThrow(new DataIntegrityViolationException(
                            "ERROR: null value in column \"title\"\n  Detail: Failing row contains (secret-row-value)"));

            int savedCount = kopisSyncService.sync();

            assertThat(savedCount).isEqualTo(1); // PF2만 저장
            verify(client).fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC"));
            verify(client).fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD"));
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.contains("BAD") && m.contains("null value in column"))
                    .noneMatch(m -> m.contains("secret-row-value"));
        } finally {
            detach(appender);
        }
    }

    // 날짜 형식이 어긋난 행은 파서에서 걸러져 다른 행·장르가 계속 처리된다(과거: 예외가 전파돼 전체가 중단됨)
    @Test
    void sync_continuesPastRowWithMalformedDate() {
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCA")))
                .thenReturn(parseXml(dbs(db("BADDATE", "2026-12-11"), db("PF2", "2026.12.11"))));
        when(client.fetchListDocument(anyString(), anyString(), eq(2), eq(100), eq("CCCA")))
                .thenReturn(parseXml(EMPTY_XML));
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC")))
                .thenReturn(parseXml(EMPTY_XML));
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD")))
                .thenReturn(parseXml(EMPTY_XML));
        when(concertRepository.findByExternalId("PF2")).thenReturn(Optional.empty());

        int savedCount = kopisSyncService.sync();

        assertThat(savedCount).isEqualTo(1);
        verify(concertRepository, never()).findByExternalId("BADDATE");
        verify(client).fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC"));
    }

    // 안전판: 페이지가 끝없이 이어져도 상한(50)에서 멈추고 경고를 남긴다
    @Test
    void sync_stopsAtPageCapAndWarns() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        try {
            when(client.fetchListDocument(anyString(), anyString(), anyInt(), eq(100), eq("CCCA")))
                    .thenAnswer(inv -> parseXml(ONE_ITEM_XML));
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCC")))
                    .thenReturn(parseXml(EMPTY_XML));
            when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), eq("CCCD")))
                    .thenReturn(parseXml(EMPTY_XML));
            when(concertRepository.findByExternalId("PF1")).thenReturn(Optional.empty());

            kopisSyncService.sync();

            verify(client, times(50)).fetchListDocument(anyString(), anyString(), anyInt(), eq(100), eq("CCCA"));
            verify(client, never()).fetchListDocument(anyString(), anyString(), eq(51), eq(100), eq("CCCA"));
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(m -> m.contains("페이지 상한") && m.contains("CCCA"));
        } finally {
            detach(appender);
        }
    }

    // 실패로 끝나도 실행 중 플래그가 풀려 다음 실행이 Conflict 없이 시작된다
    @Test
    void sync_releasesRunningFlagAfterFailure() {
        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), anyString()))
                .thenThrow(new KopisSyncException("장애"))
                .thenThrow(new KopisSyncException("장애"))
                .thenThrow(new KopisSyncException("장애"))
                .thenReturn(parseXml(EMPTY_XML));

        assertThatThrownBy(() -> kopisSyncService.sync()).isInstanceOf(KopisSyncException.class);

        assertThat(kopisSyncService.sync()).isZero(); // 두 번째 실행은 ConflictException이 아니라 정상 수행
    }

    @Test
    void sync_throwsConflictWhenAlreadyRunning() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(client.fetchListDocument(anyString(), anyString(), eq(1), eq(100), anyString())).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return parseXml(EMPTY_XML);
        });

        Thread firstRun = new Thread(kopisSyncService::sync);
        firstRun.start();

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> kopisSyncService.sync())
                .isInstanceOf(ConflictException.class);

        release.countDown();
        firstRun.join(5000);
    }
}
