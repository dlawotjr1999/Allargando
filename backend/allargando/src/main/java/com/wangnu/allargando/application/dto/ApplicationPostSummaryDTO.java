package com.wangnu.allargando.application.dto;

import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostStatus;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/*
 * 지원서 응답에 중첩되는 모집글 요약 DTO (지원자가 소비)
 */
@Getter
@Builder
public class ApplicationPostSummaryDTO {
    private Long id;
    private String title;
    private String category;
    private LocalDateTime eventAt;
    private String location;
    private PostStatus status;

    // Post 엔티티 → 지원서용 글 요약 변환
    public static ApplicationPostSummaryDTO from(Post post) {
        return ApplicationPostSummaryDTO.builder()
                .id(post.getId())
                .title(post.getTitle())
                .category(post.getCategory())
                .eventAt(post.getEventAt())
                .location(post.getLocation())
                .status(post.getStatus())
                .build();
    }
}
