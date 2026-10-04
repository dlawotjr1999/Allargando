package com.wangnu.allargando.report.entity;

import com.wangnu.allargando.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/*
 * 신고 엔티티 — 한 유저(reporter)가 모집글 또는 유저(target)를 신고한 한 건
 * 대상은 (targetType, targetId)로만 가리키고 FK를 걸지 않는다: 대상 글·유저가 나중에 삭제돼도
 * 신고 기록이 FK 때문에 막히거나 같이 지워지지 않게 하기 위해서다(다형 참조라 FK도 걸 수 없음).
 * 같은 사람이 같은 대상을 중복 신고할 수 없다(UNIQUE).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reporter_id", "target_type", "target_id"}))
public class Report {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private ReportReason reason;

    // 신고자가 덧붙인 설명(선택)
    @Column(name = "detail", length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 신고 접수 — 처리 상태는 PENDING으로 시작
    public static Report create(User reporter, ReportTargetType targetType, Long targetId,
                                ReportReason reason, String detail) {
        Report report = new Report();
        report.reporter = reporter;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.detail = detail;
        report.status = ReportStatus.PENDING;
        return report;
    }
}
