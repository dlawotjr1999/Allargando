package com.wangnu.allargando.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.wangnu.allargando.user.entity.Career;

/*
 * Career 저장소 — 유저별 경력 전체 삭제(수정 시 전체 삭제 후 재삽입 패턴) 제공
 */
public interface CareerRepository extends JpaRepository<Career, Long> {

    // 유저의 경력 전체 삭제 (정보 수정 시 재삽입 전 초기화)
    @Modifying
    @Query("DELETE FROM Career c WHERE c.user.id = :userId")
    void deleteByUserId(Long userId);

    // 여러 유저의 경력을 배치 조회 — 지원자 목록(application 도메인)이 페이지 단위로 배치 로딩할 때 사용(BACKLOG.md #21)
    List<Career> findByUserIdIn(Collection<Long> userIds);
}
