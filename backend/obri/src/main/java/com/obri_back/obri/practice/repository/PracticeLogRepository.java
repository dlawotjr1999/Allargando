package com.obri_back.obri.practice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.obri_back.obri.practice.entity.PracticeLog;

/*
 * PracticeLog 저장소 — 작성자별 조회 제공
 */
public interface PracticeLogRepository extends JpaRepository<PracticeLog, Long> {
    Page<PracticeLog> findByUserId(Long userId, Pageable pageable); // 유저가 작성한 연습 일지
    void deleteByUserId(Long userId);                               // 회원 탈퇴 시 연습 일지 전부 정리용
}
