package com.obri_back.obri.practice.controller;

import com.obri_back.obri.global.common.APIResponse;
import com.obri_back.obri.global.common.PageSupport;
import com.obri_back.obri.global.common.PageResponse;
import com.obri_back.obri.practice.dto.PracticeLogCreateRequestDTO;
import com.obri_back.obri.practice.dto.PracticeLogResponseDTO;
import com.obri_back.obri.practice.dto.PracticeLogSummaryResponseDTO;
import com.obri_back.obri.practice.service.PracticeLogService;
import com.obri_back.obri.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 연습 일지 관련 API 컨트롤러
 * POST   /api/practice-logs      — 연습 일지 등록
 * GET    /api/practice-logs      — 내 연습 일지 목록 조회 (무한스크롤, 본인 것만)
 * GET    /api/practice-logs/{id} — 연습 일지 단건 조회 (본인만)
 * PUT    /api/practice-logs/{id} — 연습 일지 수정 (본인만)
 * DELETE /api/practice-logs/{id} — 연습 일지 삭제 (본인만)
 */
@RestController
@RequestMapping("/api/practice-logs")
@RequiredArgsConstructor
public class PracticeLogController {

    private final PracticeLogService practiceLogService;

    // 연습 일지 등록
    @PostMapping
    public ResponseEntity<APIResponse<PracticeLogResponseDTO>> createPracticeLog(
            @AuthenticationPrincipal User user,
            @RequestBody @Valid PracticeLogCreateRequestDTO request) {
        PracticeLogResponseDTO response = practiceLogService.createPracticeLog(user, request);
        return ResponseEntity.ok(APIResponse.ok("연습 일지가 등록되었습니다", response));
    }

    // 내 연습 일지 목록 조회 — 공개 목록이 없으므로 항상 본인 것만 반환
    @GetMapping
    public ResponseEntity<APIResponse<PageResponse<PracticeLogSummaryResponseDTO>>> getPracticeLogs(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<PracticeLogSummaryResponseDTO> response = practiceLogService.getMyPracticeLogs(
                user.getId(), PageSupport.withSort(pageable, Sort.by(Sort.Direction.DESC, "logDate")));
        return ResponseEntity.ok(APIResponse.ok("연습 일지 목록 조회 성공", PageResponse.from(response)));
    }

    // 연습 일지 단건 조회 (본인만)
    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<PracticeLogResponseDTO>> getPracticeLog(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        PracticeLogResponseDTO response = practiceLogService.getPracticeLog(id, user);
        return ResponseEntity.ok(APIResponse.ok("연습 일지 조회 성공", response));
    }

    // 연습 일지 수정 (본인만, 전체 필드 교체)
    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<PracticeLogResponseDTO>> updatePracticeLog(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody @Valid PracticeLogCreateRequestDTO request) {
        PracticeLogResponseDTO response = practiceLogService.updatePracticeLog(id, user, request);
        return ResponseEntity.ok(APIResponse.ok("연습 일지가 수정되었습니다", response));
    }

    // 연습 일지 삭제 (본인만)
    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<Void>> deletePracticeLog(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        practiceLogService.deletePracticeLog(id, user);
        return ResponseEntity.ok(APIResponse.ok("연습 일지가 삭제되었습니다"));
    }
}
