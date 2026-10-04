package com.wangnu.allargando.practice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.wangnu.allargando.user.entity.User;

/*
 * 연습 일지 엔티티 (User 종속)
 * 날짜·연습 시간(분)·제목·내용을 기록. 작성자 본인만 조회·수정·삭제 가능(전용 Policy 없이 서비스에서 직접 검증)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "practice_log")
public class PracticeLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "duration", nullable = false)
    private Integer duration; // 연습 시간 (분 단위)

    // length만 지정(columnDefinition 미사용) — Post.description과 동일 이유로 DB 이식성 확보
    @Column(name = "content", length = 4000)
    private String content; // 연습 내용 (선택 입력)

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 연습 일지 생성
    public static PracticeLog create(User user, String title, LocalDate logDate, Integer duration, String content) {
        PracticeLog log = new PracticeLog();
        log.user = user;
        log.title = title;
        log.logDate = logDate;
        log.duration = duration;
        log.content = content;
        return log;
    }

    // 연습 일지 수정 (전체 필드 교체)
    public void update(String title, LocalDate logDate, Integer duration, String content) {
        this.title = title;
        this.logDate = logDate;
        this.duration = duration;
        this.content = content;
    }

    // 이 일지의 작성자가 user인지 확인 — 호출부가 getUser().getId().equals(...) 체인을 직접 다루지 않도록 함(Post.isOwnedBy와 동일 패턴)
    public boolean isOwnedBy(User user) {
        return this.user.getId().equals(user.getId());
    }
}
