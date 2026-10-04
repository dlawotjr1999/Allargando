package com.wangnu.allargando.concert.kopis.dto;

import java.time.LocalDate;

/*
 * KOPIS 공연목록 응답 한 건에서 파싱한 값 — 목록 응답만으로 Concert 저장에 필요한 필드가 전부 채워져
 * 상세 페이지를 별도로 조회할 필요가 없다
 */
public record KopisPerformanceItem(
        String externalId,
        String title,
        String category,
        LocalDate startDate,
        LocalDate endDate,
        String venue,
        String region,
        String posterUrl,
        String url
) {
}
