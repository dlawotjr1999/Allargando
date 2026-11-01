package com.wangnu.allargando.practice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.wangnu.allargando.practice.entity.PracticeLog;

/*
 * PracticeLog 저장소 — 목록은 PracticeLogSpecification으로 작성자·검색 조건을 걸어 조회한다
 */
public interface PracticeLogRepository extends JpaRepository<PracticeLog, Long>, JpaSpecificationExecutor<PracticeLog> {
    void deleteByUserId(Long userId);                               // 회원 탈퇴 시 연습 일지 전부 정리용
}
