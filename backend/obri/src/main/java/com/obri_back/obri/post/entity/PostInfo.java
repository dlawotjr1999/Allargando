package com.obri_back.obri.post.entity;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/*
 * Post 생성/수정에 필요한 값 객체 — 웹 요청 검증(jakarta.validation)과 무관한 내부 전달용
 * PostService가 PostCreateRequestDTO에서 값을 꺼내 조립해 Post.create()/updateInfo()에 넘긴다
 */
@Getter
@Builder
public class PostInfo {
    private String category;
    private String title;
    private LocalDateTime eventAt;
    private String location;
    private String region;
    private String timetable;
    private String description;
}
