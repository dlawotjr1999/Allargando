package com.obri_back.obri.post.dto;

import com.obri_back.obri.application.entity.ApplicationStatus;
import com.obri_back.obri.post.entity.Post;
import com.obri_back.obri.post.entity.PostStatus;
import com.obri_back.obri.user.entity.User;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// GET /api/posts/{id} 단건 조회 전용 — 목록 조회(PostSummaryResponseDTO)에는 없는
// writer, applicationCount, isMine, hasApplied 포함
@Getter
@Builder
public class PostDetailResponseDTO {
    private Long id;
    private Writer writer;
    private Long applicationCount;
    private Boolean isMine;
    // 지원한 적이 있는지 — myApplicationStatus != null과 같다(기존 클라이언트 호환용으로 유지)
    private Boolean hasApplied;
    // 내 지원 상태(없으면 null). 취소(CANCELLED)만 다시 지원할 수 있고 거절·철회는 불가라 버튼 문구를 가르는 데 쓴다(D7)
    private ApplicationStatus myApplicationStatus;
    private String category;
    private String title;
    private LocalDateTime eventAt;
    private String location;
    private String region;
    private String timetable;
    private String description;
    private PostStatus status;
    private List<PostInstrumentDTO> instruments;
    private LocalDateTime createdAt;

    // Post 엔티티 + 계산값(지원자 수·본인 여부·내 지원 상태) → 상세 DTO 변환
    public static PostDetailResponseDTO from(Post post, long applicationCount, boolean isMine,
            ApplicationStatus myApplicationStatus) {
        return PostDetailResponseDTO.builder()
                .id(post.getId())
                .writer(Writer.from(post.getUser()))
                .applicationCount(applicationCount)
                .isMine(isMine)
                .hasApplied(myApplicationStatus != null)
                .myApplicationStatus(myApplicationStatus)
                .category(post.getCategory())
                .title(post.getTitle())
                .eventAt(post.getEventAt())
                .location(post.getLocation())
                .region(post.getRegion())
                .timetable(post.getTimetable())
                .description(post.getDescription())
                .status(post.getStatus())
                .instruments(post.getPostInstruments().stream()
                        .map(PostInstrumentDTO::from)
                        .collect(Collectors.toList()))
                .createdAt(post.getCreatedAt())
                .build();
    }

    // 작성자 요약 정보 (닉네임·전공 악기만 노출)
    @Getter
    @Builder
    public static class Writer {
        private String nickname;
        private String instrument;

        // User 엔티티 → 작성자 요약 변환
        public static Writer from(User user) {
            return Writer.builder()
                    .nickname(user.getNickname())
                    .instrument(user.getInstrument())
                    .build();
        }
    }
}
