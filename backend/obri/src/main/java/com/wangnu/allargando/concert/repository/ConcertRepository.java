package com.wangnu.allargando.concert.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.wangnu.allargando.concert.entity.Concert;

/*
 * Concert 저장소 — 동적 필터(JpaSpecificationExecutor) 제공
 */
public interface ConcertRepository extends JpaRepository<Concert, Long>, JpaSpecificationExecutor<Concert> {
    Optional<Concert> findByExternalId(String externalId); // KOPIS 동기화 신규/기존 판별

    // 종료일이 cutoff보다 이전인 공연 삭제 — 동기화 때 오래된 공연 정리용. 삭제한 건수 반환.
    // 동기화 서비스는 트랜잭션 없이 도는 구조라 이 벌크 삭제가 스스로 트랜잭션을 연다
    @Transactional
    @Modifying
    @Query("delete from Concert c where c.endDate < :cutoff")
    int deleteByEndDateBefore(@Param("cutoff") LocalDate cutoff);
}
