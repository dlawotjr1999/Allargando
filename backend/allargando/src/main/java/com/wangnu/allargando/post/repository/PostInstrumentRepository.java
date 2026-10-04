package com.wangnu.allargando.post.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wangnu.allargando.post.entity.PostInstrument;

/*
 * PostInstrument 저장소 — 모집글별 모집 악기 목록 조회 제공
 */
public interface PostInstrumentRepository extends JpaRepository<PostInstrument, Long> {
    List<PostInstrument> findByPostId(Long postId);  // 모집글의 악기 목록
}
