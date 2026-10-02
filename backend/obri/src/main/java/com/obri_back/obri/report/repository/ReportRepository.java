package com.obri_back.obri.report.repository;

import com.obri_back.obri.report.entity.Report;
import com.obri_back.obri.report.entity.ReportTargetType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/*
 * Report 저장소 — 중복 신고 체크·탈퇴 시 정리 제공
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    // DB UNIQUE 제약 전에 애플리케이션 레벨에서 먼저 차단해 409 에러 메시지를 제어
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    // 회원 탈퇴 시 이 유저가 한 신고와 이 유저를 대상으로 한 신고를 삭제.
    // 이 유저가 쓴 모집글에 대한 신고는 남는다 — 글 id와 사유만 있고 개인정보가 없으며, 지우려면 post 도메인을 알아야 해서 범위 밖
    @Modifying(flushAutomatically = true)
    @Query("delete from Report r where r.reporter.id = :userId " +
           "or (r.targetType = :userType and r.targetId = :userId)")
    void deleteAllInvolving(@Param("userId") Long userId, @Param("userType") ReportTargetType userType);
}
