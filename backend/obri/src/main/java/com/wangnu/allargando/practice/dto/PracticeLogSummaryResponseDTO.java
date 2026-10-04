package com.wangnu.allargando.practice.dto;

import com.wangnu.allargando.practice.entity.PracticeLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/*
 * 연습 일지 목록 조회용 요약 응답 DTO (content 등 상세 필드 제외)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeLogSummaryResponseDTO {

    private Long id;
    private String title;
    private LocalDate logDate;
    private Integer duration;

    // PracticeLog 엔티티 → 요약 DTO 변환
    public static PracticeLogSummaryResponseDTO from(PracticeLog log) {
        return PracticeLogSummaryResponseDTO.builder()
                .id(log.getId())
                .title(log.getTitle())
                .logDate(log.getLogDate())
                .duration(log.getDuration())
                .build();
    }
}
