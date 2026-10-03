package com.obri_back.obri.application.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.obri_back.obri.application.entity.Application;
import com.obri_back.obri.application.entity.ApplicationStatus;

/*
 * Application 저장소 — 지원자별/모집글별 조회, 중복 지원 체크, 알림 대상 토큰 조회 제공
 */
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    // 모집글 수정 알림 대상(지정 상태) 지원자의 fcm_token 조회 — 토큰 없는 유저는 제외
    @Query("SELECT a.user.fcmToken FROM Application a " +
           "WHERE a.post.id = :postId AND a.status IN :statuses AND a.user.fcmToken IS NOT NULL")
    List<String> findApplicantFcmTokens(@Param("postId") Long postId,
                                        @Param("statuses") Collection<ApplicationStatus> statuses);

    // 목록 응답(AppResponseDTO)이 post·user를 매핑하므로 to-one 연관을 함께 로딩해 N+1 방지
    @EntityGraph(attributePaths = {"post", "user"})
    Page<Application> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "user"})
    Page<Application> findByPostId(Long postId, Pageable pageable);
    // DB UNIQUE 제약 전에 애플리케이션 레벨에서 먼저 차단해 409 에러 메시지를 제어(아래 findByPostIdAndUserId로 확인)
    // 같은 글에 낸 내 지원 — 재지원(취소 복구)·내 지원 상태 조회용
    Optional<Application> findByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    void deleteByPostId(Long postId);

    // 회원 탈퇴 시 이 유저가 낸 지원서 정리용 — 수락된 지원은 모집글 악기 확정 인원을 되돌려야 하므로
    // post를 함께 로딩해 상태별로 먼저 조회한 뒤 삭제한다
    @EntityGraph(attributePaths = {"post"})
    List<Application> findByUserIdAndStatus(Long userId, ApplicationStatus status);
    void deleteByUserId(Long userId);

    // 같은 글·같은 악기의 특정 상태 지원 — 정원 마감 시 남은 대기(PENDING) 지원을 한꺼번에 거절하는 데 쓴다.
    // 거절 알림에 지원자의 FCM 토큰이 필요해 user를 함께 로딩한다
    @EntityGraph(attributePaths = {"user"})
    List<Application> findByPostIdAndInstrumentAndStatus(Long postId, String instrument, ApplicationStatus status);
}
