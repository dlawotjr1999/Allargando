package com.wangnu.allargando.practice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/*
 * 연습 일지 등록/수정 요청 바디 (POST·PUT /api/practice-logs)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeLogCreateRequestDTO {

    @NotBlank
    private String title;

    @NotNull
    private LocalDate logDate;

    @NotNull
    @Min(1)
    private Integer duration; // 연습 시간 (분 단위)

    // 연습 내용 (선택 입력)
    private String content;

    // 연습 분류 (선택 입력) — 수정(PUT)은 전체 교체라 보내지 않으면 분류가 지워진다
    @Size(max = 20, message = "분류는 20자 이내여야 합니다")
    private String category;
}
