package com.wangnu.allargando.practice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.wangnu.allargando.user.entity.User;

/*
 * 연습 피드백 엔티티 (User 종속)
 * 기간별 연습 기록에 대한 LLM 피드백 저장용 — 향후 기능 스캐폴딩
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "practice_feedback")
public class PracticeFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "period_start")
    private String periodStart; // 연습 기간 시작

    @Column(name = "period_end")
    private String periodEnd; // 연습 기간 종료

    @Column(name = "feedback")
    private String feedback; // 피드백 내용

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
