package com.wangnu.allargando.post.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.wangnu.allargando.post.entity.Post;

/*
 * Post 저장소 — 동적 필터(JpaSpecificationExecutor)·작성자별 조회 제공
 */
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    Page<Post> findByUserId(Long userId, Pageable pageable);      // 유저가 작성한 모집글
    List<Post> findByUserId(Long userId);                         // 회원 탈퇴 시 작성한 모집글 전부 정리용
}
