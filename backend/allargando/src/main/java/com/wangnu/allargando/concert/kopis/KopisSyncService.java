package com.wangnu.allargando.concert.kopis;

import com.wangnu.allargando.concert.entity.Concert;
import com.wangnu.allargando.concert.kopis.dto.KopisPerformanceItem;
import com.wangnu.allargando.concert.repository.ConcertRepository;
import com.wangnu.allargando.global.exception.ConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * KOPIS 동기화 오케스트레이션 — 오늘부터 SYNC_MONTHS_AHEAD개월 이내, 음악 계열 장르(GENRE_CODES)의
 * 공연을 장르별·페이지 단위로 가져와 신규 저장·기존 갱신을 수행한다. 목록 응답만으로 Concert 저장에
 * 필요한 필드가 전부 채워져 상세 조회가 없고, 빈 페이지를 만나면 그 장르는 끝난
 * 것으로 본다. 조회 실패는 빈 페이지와 구분해 그 장르만 중단·실패로 기록하고, 모든 장르가 실패하면
 * 정상 종료로 보이지 않도록 예외를 던진다.
 * KOPIS API가 장르(shcate) 다중값을 지원하지 않아 장르 하나당 별도로 전체 페이지를 순회한다.
 * 스케줄러·수동 트리거가 공유하는 진입점이라 동시 실행은 AtomicBoolean으로 차단
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KopisSyncService {

    private static final DateTimeFormatter KOPIS_QUERY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SYNC_MONTHS_AHEAD = 6;
    // 종료된 지 이 일수가 지난 공연은 동기화 때 삭제한다(D18). 목록 조회는 종료된 공연을 이미 가리므로 노출과 무관한 정리다
    private static final int RETENTION_DAYS_AFTER_END = 90;
    private static final int ROWS_PER_PAGE = 100;
    // 안전판 — 6개월치 공연이 장르당 5000건(50페이지 x 100건)을 넘길 일은 없다고 봄
    private static final int HARD_CAP_PAGE = 50;
    // 악기 연주와 관련 있는 음악 계열 장르만 대상. CCCA(서양음악/클래식)·CCCC(한국음악/국악)·CCCD(대중음악)
    // 모두 실제 응답으로 확인했다(verify-runtime.md §5)
    private static final List<String> GENRE_CODES = List.of("CCCA", "CCCC", "CCCD");

    private final KopisClient client;
    private final ConcertRepository concertRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /*
     * 동기화 1회 실행 — 저장된 신규 건수 반환
     * 건별 커밋(트랜잭션 없음)이 의도다: 중간에 실패해도 이미 저장한 건은 남고, upsert가 멱등이라 다음 실행이 이어서 반영한다.
     * 동시 실행 가드(AtomicBoolean)는 인스턴스 로컬이다 — v1은 서버 1대 전제(RELEASE_PLAN)라 충분하고,
     * 서버를 늘리면 ConcertRepository의 UNIQUE(external_id) 충돌 처리와 함께 분산 락을 다시 봐야 한다.
     *
     * @throws KopisSyncException 모든 장르의 목록 조회가 실패했을 때(잘못된 서비스키·KOPIS 장애 등)
     * @throws ConflictException  이미 실행 중일 때
     */
    public int sync() {
        if (!running.compareAndSet(false, true)) {
            throw new ConflictException("이미 KOPIS 동기화가 진행 중입니다");
        }
        try {
            return syncAllGenres();
        } finally {
            running.set(false);
        }
    }

    private int syncAllGenres() {
        String stdate = LocalDate.now().format(KOPIS_QUERY_DATE);
        String eddate = LocalDate.now().plusMonths(SYNC_MONTHS_AHEAD).format(KOPIS_QUERY_DATE);

        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<String> failedGenres = new ArrayList<>();

        for (String genreCode : GENRE_CODES) {
            GenreSyncResult result = syncGenrePages(stdate, eddate, genreCode);
            newCount += result.newCount();
            updatedCount += result.updatedCount();
            skippedCount += result.skippedCount();
            if (result.failed()) {
                failedGenres.add(genreCode);
            }
        }

        // 전 장르 실패는 "신규 0건 성공"처럼 보이면 안 되므로 호출부(스케줄러 로그·수동 트리거 응답)에 실패를 드러낸다
        if (failedGenres.size() == GENRE_CODES.size()) {
            throw new KopisSyncException("KOPIS 동기화 실패: 모든 장르의 목록 조회에 실패했습니다 (신규 "
                    + newCount + "건 저장, 기존 " + updatedCount + "건 갱신)");
        }
        if (!failedGenres.isEmpty()) {
            log.warn("KOPIS 동기화 일부 실패 — 실패 장르 {}", failedGenres);
        }
        purgeExpiredConcerts();
        log.info("KOPIS 동기화 완료 — 신규 {}건 저장, 기존 {}건 갱신, 건너뜀 {}건", newCount, updatedCount, skippedCount);
        return newCount;
    }

    // 종료 90일이 지난 공연 삭제 — end_date 기준이라 일부 장르가 실패한 회차에도 안전하다(syncedAt은 기준으로 쓰지 않는다).
    // 정리 실패가 동기화 결과를 뒤집지 않도록 예외는 로그로만 남긴다
    private void purgeExpiredConcerts() {
        try {
            int deleted = concertRepository.deleteByEndDateBefore(LocalDate.now().minusDays(RETENTION_DAYS_AFTER_END));
            if (deleted > 0) {
                log.info("종료된 지 {}일이 지난 공연 {}건 삭제", RETENTION_DAYS_AFTER_END, deleted);
            }
        } catch (RuntimeException e) {
            log.warn("종료 공연 정리 실패 — 다음 동기화에서 다시 시도", e);
        }
    }

    // failed = 목록 조회 실패(잘못된 키·네트워크·오류 응답)로 중단한 장르. 빈 페이지로 정상 종료한 장르는 false
    private record GenreSyncResult(int newCount, int updatedCount, int skippedCount, boolean failed) {
    }

    private GenreSyncResult syncGenrePages(String stdate, String eddate, String genreCode) {
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        boolean reachedEnd = false;

        for (int page = 1; page <= HARD_CAP_PAGE; page++) {
            List<KopisPerformanceItem> items;
            try {
                items = KopisResponseParser.parse(
                        client.fetchListDocument(stdate, eddate, page, ROWS_PER_PAGE, genreCode));
            } catch (KopisSyncException e) {
                // 조회 실패 — 이 장르만 중단(이미 저장한 페이지는 유지), 다음 스케줄 회차에서 처음부터 재시도
                log.warn("KOPIS 목록 페이지 조회 실패: genre={}, page={}", genreCode, page, e);
                return new GenreSyncResult(newCount, updatedCount, skippedCount, true);
            }
            if (items.isEmpty()) {
                reachedEnd = true;
                break; // 빈 페이지 — 이 장르는 정상적으로 끝남
            }

            for (KopisPerformanceItem item : items) {
                try {
                    if (saveOrUpdate(item)) {
                        newCount++;
                    } else {
                        updatedCount++;
                    }
                } catch (DataIntegrityViolationException e) {
                    // 필수 필드 누락(NOT NULL)·길이 초과 같은 불량 행은 그 행만 건너뛴다 — 한 행 때문에 이후 행·페이지·장르가 멈추지 않게
                    skippedCount++;
                    log.warn("KOPIS 공연 저장 실패로 건너뜀: externalId={}, 원인={}", item.externalId(),
                            firstLine(e.getMostSpecificCause().getMessage()));
                }
            }
        }

        if (!reachedEnd) {
            log.warn("KOPIS 페이지 상한({}) 도달 — 이 장르의 나머지 공연은 반영되지 않았을 수 있음: genre={}",
                    HARD_CAP_PAGE, genreCode);
        }
        return new GenreSyncResult(newCount, updatedCount, skippedCount, false);
    }

    // 예외 메시지의 첫 줄만 — PostgreSQL은 둘째 줄부터 행 값을 담으므로 로그에는 원인 요약만 남긴다
    private String firstLine(String message) {
        return message == null ? "" : message.lines().findFirst().orElse("");
    }

    // true = 신규 저장, false = 기존 갱신
    private boolean saveOrUpdate(KopisPerformanceItem item) {
        Optional<Concert> existing = concertRepository.findByExternalId(item.externalId());
        if (existing.isPresent()) {
            Concert concert = existing.get();
            concert.updateFromSync(item.title(), item.category(), item.startDate(), item.endDate(),
                    item.venue(), item.region(), item.posterUrl(), item.url());
            concertRepository.save(concert);
            return false;
        }

        concertRepository.save(Concert.fromSync(item.externalId(), item.title(), item.category(),
                item.startDate(), item.endDate(), item.venue(), item.region(), item.posterUrl(), item.url()));
        return true;
    }
}
