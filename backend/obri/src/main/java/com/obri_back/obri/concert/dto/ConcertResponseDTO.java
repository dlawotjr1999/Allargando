package com.obri_back.obri.concert.dto;

import com.obri_back.obri.concert.entity.Concert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/*
 * 연주회 응답 DTO — 목록·단건 조회 공용
 * KOPIS 동기화 데이터라 필드 수가 적고 목록/단건 간 차이(applicationCount 같은 계산값)가 없어 DTO를 나누지 않음
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertResponseDTO {
    private Long id;
    private String title;
    private String category;
    private LocalDate startDate;
    private LocalDate endDate;
    private String venue;
    private String region;
    private String posterUrl;
    private String sourceUrl;

    // Concert 엔티티 → 응답 DTO 변환
    public static ConcertResponseDTO from(Concert concert) {
        return ConcertResponseDTO.builder()
                .id(concert.getId())
                .title(concert.getTitle())
                .category(concert.getCategory())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .venue(concert.getVenue())
                .region(concert.getRegion())
                .posterUrl(concert.getPosterUrl())
                .sourceUrl(concert.getUrl())
                .build();
    }
}
