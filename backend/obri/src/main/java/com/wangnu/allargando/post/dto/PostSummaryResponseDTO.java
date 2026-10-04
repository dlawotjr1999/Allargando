package com.wangnu.allargando.post.dto;

import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/*
 * 모집글 목록/마이페이지용 요약 응답 DTO (writer·applicationCount 등 상세 필드 제외)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostSummaryResponseDTO {
    private Long id;
    private String title;
    private String category;
    private LocalDateTime eventAt;
    private String location;
    private String region;
    private List<PostInstrumentDTO> instruments;
    private String timetable;
    private PostStatus status;

    // Post 엔티티 → 요약 DTO 변환
    public static PostSummaryResponseDTO from(Post post) {
        return PostSummaryResponseDTO.builder()
                .id(post.getId())
                .title(post.getTitle())
                .category(post.getCategory())
                .eventAt(post.getEventAt())
                .location(post.getLocation())
                .region(post.getRegion())
                .instruments(post.getPostInstruments().stream()
                        .map(PostInstrumentDTO::from)
                        .collect(Collectors.toList()))
                .timetable(post.getTimetable())
                .status(post.getStatus())
                .build();
    }
}
