package com.wangnu.allargando.post.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
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

    @NotBlank(message = "카테고리를 선택해 주세요")
    @Size(max = 255, message = "카테고리는 255자 이내여야 합니다")
    private String category;

    @NotBlank(message = "제목을 입력해 주세요")
    @Size(max = 255, message = "제목은 255자 이내여야 합니다")
    private String title;

    // 과거 공연일로는 등록·수정할 수 없다 — 목록에서 바로 빠지고 지원도 막혀 작성자에게만 보이는 죽은 글이 된다
    @NotNull(message = "공연 일시를 입력해 주세요")
    @Future(message = "공연 일시는 현재 이후여야 합니다")
    private LocalDateTime eventAt;

    @NotBlank(message = "장소를 입력해 주세요")
    @Size(max = 255, message = "장소는 255자 이내여야 합니다")
    private String location;

    // 지역 필터 전용 값 (프론트 지역 선택 UI에서 제공) — BACKLOG.md #38
    @NotBlank(message = "지역을 선택해 주세요")
    @Size(max = 255, message = "지역은 255자 이내여야 합니다")
    private String region;

    @NotBlank(message = "시간표를 입력해 주세요")
    @Size(max = 255, message = "시간표는 255자 이내여야 합니다")
    private String timetable;

    // 모집글 상세 설명 (선택 입력) — BACKLOG.md #34
    @Size(max = 2000, message = "설명은 2000자 이내여야 합니다")
    private String description;

    @NotNull(message = "모집 악기를 입력해 주세요")
    @Size(min = 1, message = "모집 악기를 1개 이상 입력해 주세요")
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
        @NotBlank(message = "악기를 선택해 주세요")
        @Size(max = 255, message = "악기는 255자 이내여야 합니다")
        private String instrument;

        @NotNull(message = "모집 인원을 입력해 주세요")
        @Min(value = 1, message = "모집 인원은 1명 이상이어야 합니다")
        @Max(value = 100, message = "모집 인원은 100명 이하여야 합니다")
        private Integer people;
    }
}
