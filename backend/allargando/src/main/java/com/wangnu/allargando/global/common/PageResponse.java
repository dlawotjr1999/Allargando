package com.wangnu.allargando.global.common;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 무한스크롤용 페이지네이션 응답 포맷
 * Spring Data Page의 기본 JSON(totalPages 등)을 그대로 노출하지 않고
 * content, hasNext, currentPage만 반환 (CLAUDE.md 4장 API 규칙)
 */
@Getter
@Builder
public class PageResponse<T> {
    private List<T> content;
    private boolean hasNext;
    private int currentPage;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .hasNext(page.hasNext())
                .currentPage(page.getNumber())
                .build();
    }
}
