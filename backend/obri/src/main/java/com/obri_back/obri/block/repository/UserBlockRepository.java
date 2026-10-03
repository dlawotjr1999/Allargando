package com.obri_back.obri.block.repository;

import com.obri_back.obri.block.entity.UserBlock;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/*
 * UserBlock 저장소 — 차단 여부 확인·차단 목록 조회·탈퇴 시 정리 제공
 */
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 차단 목록 응답이 차단당한 유저(닉네임·악기)를 매핑하므로 함께 로딩해 N+1 방지
    @EntityGraph(attributePaths = {"blocked"})
    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    // 회원 탈퇴 시 이 유저가 차단한 기록과 이 유저가 차단당한 기록을 모두 삭제
    @Modifying(flushAutomatically = true)
    @Query("delete from UserBlock b where b.blocker.id = :userId or b.blocked.id = :userId")
    void deleteAllInvolving(@Param("userId") Long userId);
}
