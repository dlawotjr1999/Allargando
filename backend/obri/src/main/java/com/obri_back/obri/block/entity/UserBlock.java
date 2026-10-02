package com.obri_back.obri.block.entity;

import com.obri_back.obri.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 유저 차단 엔티티 — blocker(차단한 사람)가 blocked(차단당한 사람)를 차단한 한 건
 * 방향이 있다: A가 B를 차단해도 B가 A를 차단한 것은 아니다. 같은 쌍은 한 번만 가능(UNIQUE)
 * User를 단방향 @ManyToOne으로만 참조한다(CLAUDE.md §3.5)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "user_block",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 차단 생성
    public static UserBlock of(User blocker, User blocked) {
        UserBlock block = new UserBlock();
        block.blocker = blocker;
        block.blocked = blocked;
        return block;
    }
}
