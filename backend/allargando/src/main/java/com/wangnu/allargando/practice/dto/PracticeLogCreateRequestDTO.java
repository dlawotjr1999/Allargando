package com.wangnu.allargando.practice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
}
