package com.wangnu.allargando.report.repository;

import com.wangnu.allargando.report.entity.Report;
import com.wangnu.allargando.report.entity.ReportTargetType;

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

    // 회원 탈퇴 시 이 유저가 한 신고, 이 유저를 대상으로 한 신고, 이 유저가 쓴 모집글을 대상으로 한 신고를 삭제(D18 —
    // "탈퇴 = 남긴 데이터 전부 삭제"). 신고 설명(detail)에 글 작성자를 알 수 있는 내용이 섞일 수 있어 남기지 않는다.
    // 글 id는 서브쿼리로 구하므로 글이 지워지기 전에 실행돼야 한다 — ReportService.onUserWithdrawal이 가장 먼저 돈다
    @Modifying(flushAutomatically = true)
    @Query("delete from Report r where r.reporter.id = :userId " +
           "or (r.targetType = :userType and r.targetId = :userId) " +
           "or (r.targetType = :postType and r.targetId in (select p.id from Post p where p.user.id = :userId))")
    void deleteAllInvolving(@Param("userId") Long userId, @Param("userType") ReportTargetType userType,
                            @Param("postType") ReportTargetType postType);
}
