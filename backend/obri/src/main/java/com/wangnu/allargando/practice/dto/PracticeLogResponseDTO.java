package com.wangnu.allargando.practice.dto;

import com.wangnu.allargando.practice.entity.PracticeLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * 연습 일지 응답 DTO — 생성·수정·단건 조회 공통 사용(작성자 본인만 접근하므로 Post.Detail처럼
 * 뷰어별 계산 필드가 필요 없어 별도 Detail DTO를 두지 않음)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeLogResponseDTO {

    private Long id;
    private String title;
    private LocalDate logDate;
    private Integer duration;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // PracticeLog 엔티티 → 응답 DTO 변환
    public static PracticeLogResponseDTO from(PracticeLog log) {
        return PracticeLogResponseDTO.builder()
                .id(log.getId())
                .title(log.getTitle())
                .logDate(log.getLogDate())
                .duration(log.getDuration())
                .content(log.getContent())
                .createdAt(log.getCreatedAt())
                .updatedAt(log.getUpdatedAt())
                .build();
    }
}
