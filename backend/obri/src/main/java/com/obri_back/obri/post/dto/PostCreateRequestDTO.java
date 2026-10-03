package com.obri_back.obri.post.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 모집글 등록/수정 요청 바디 (POST·PUT /api/posts)
 * 모집 악기는 중첩 InstrumentItem 리스트로 받음
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequestDTO {

    @NotBlank
    private String category;

    @NotBlank
    private String title;

    @NotNull
    private LocalDateTime eventAt;

    @NotBlank
    private String location;

    // 지역 필터 전용 값 (프론트 지역 선택 UI에서 제공) — BACKLOG.md #38
    @NotBlank
    private String region;

    @NotBlank
    private String timetable;

    // 모집글 상세 설명 (선택 입력) — BACKLOG.md #34
    private String description;

    @NotNull
    @Size(min = 1)
    @Valid
    private List<InstrumentItem> instruments;

    // 악기명 중복 등록 차단 — Post.replaceInstruments가 이름을 키로 병합하므로 중복 시
    // 확정 인원(confirmed)·마감 상태가 뒤섞인다(2026-08-17 발견된 유입 버그)
    @AssertTrue(message = "악기명은 중복될 수 없습니다")
    private boolean isInstrumentsUnique() {
        if (instruments == null) return true;
        long distinctCount = instruments.stream()
                .map(InstrumentItem::getInstrument)
                .distinct()
                .count();
        return distinctCount == instruments.size();
    }

    // 모집 악기 1건 (악기명 + 모집 인원)
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstrumentItem {
        @NotBlank
        private String instrument;

        @NotNull
        @Min(1)
        private Integer people;
    }
}
