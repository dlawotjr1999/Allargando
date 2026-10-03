package com.obri_back.obri.concert.controller;

import com.obri_back.obri.concert.dto.ConcertResponseDTO;
import com.obri_back.obri.concert.kopis.KopisSyncService;
import com.obri_back.obri.concert.service.ConcertService;
import com.obri_back.obri.global.common.APIResponse;
import com.obri_back.obri.global.common.PageResponse;
import com.obri_back.obri.global.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 연주회 관련 API 컨트롤러
 * GET  /api/concerts        — 연주회 전체 조회 (카테고리·지역·기간 필터·페이지네이션)
 * GET  /api/concerts/{id}   — 연주회 단건 조회
 * POST /api/concerts/sync   — KOPIS 동기화 수동 트리거 (개발/검증용, 운영에서는 비활성. 정기 실행은 스케줄러가 담당)
 */
@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;
    private final KopisSyncService kopisSyncService;

    // 수동 트리거 허용 여부 — 운영 기본값은 false(kopis.sync.manual-trigger-enabled)
    @Value("${kopis.sync.manual-trigger-enabled:false}")
    private boolean manualSyncEnabled;

    // 연주회 전체 조회 (카테고리·지역 필터 + 기간 + 페이지네이션, 기본 정렬은 공연 임박순. ?sort=로 재정의 가능)
    @GetMapping
    public ResponseEntity<APIResponse<PageResponse<ConcertResponseDTO>>> getConcertList(
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> region,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<ConcertResponseDTO> response =
                concertService.getConcertList(category, region, fromDate, toDate, pageable);
        return ResponseEntity.ok(APIResponse.ok("연주회 목록 조회 성공", PageResponse.from(response)));
    }

    // 연주회 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<ConcertResponseDTO>> getConcert(@PathVariable Long id) {
        ConcertResponseDTO response = concertService.getConcert(id);
        return ResponseEntity.ok(APIResponse.ok("연주회 조회 성공", response));
    }

    // KOPIS 동기화 수동 트리거 — 개발자 전용(Swagger/curl, 프론트 미호출).
    // role 체계가 없어 켜져 있으면 로그인한 누구나 호출할 수 있으므로, 운영에서는 설정으로 꺼두고 404로 응답한다.
    // 중복 실행은 KopisSyncService에서 409로 차단
    @PostMapping("/sync")
    public ResponseEntity<APIResponse<Map<String, Integer>>> triggerSync() {
        if (!manualSyncEnabled) {
            throw new NotFoundException("요청한 리소스를 찾을 수 없습니다");
        }
        int savedCount = kopisSyncService.sync();
        return ResponseEntity.ok(APIResponse.ok("KOPIS 동기화 완료", Map.of("savedCount", savedCount)));
    }
}
