package com.obri_back.obri.application.entity;

import com.obri_back.obri.post.entity.Post;
import com.obri_back.obri.user.entity.User;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
// (post_id, user_id) 조합 UNIQUE: 중복 지원 DB 레벨 최종 방어선
@Table(name = "application", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"post_id", "user_id"})
})
public class Application {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "instrument", nullable = false)
    private String instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApplicationStatus status;

    @Column(name = "additional_info")
    private String additionalInfo;

    // 낙관적 락: 동시 상태 변경 충돌 방지
    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 엔티티 불변성 유지를 위해 setter 대신 전용 메서드로 상태 변경
    public void updateStatus(ApplicationStatus status) {
        this.status = status;
    }

    // 취소했던 지원을 다시 지원으로 되돌린다(D7) — 새 행을 만들지 않고 같은 행을 PENDING으로 복구해
    // UNIQUE(post_id, user_id)를 건드리지 않는다. 악기·어필 문구는 새로 고른 값으로 갱신한다
    public void reapply(String instrument, String additionalInfo) {
        this.instrument = instrument;
        this.additionalInfo = additionalInfo;
        this.status = ApplicationStatus.PENDING;
    }

    // 이 지원서의 지원자가 user인지 확인 — 호출부가 getUser().getId().equals(...) 체인을 직접 다루지 않도록 함(CLAUDE.md §8)
    public boolean isApplicant(User user) {
        return this.user.getId().equals(user.getId());
    }

    // 이 지원서가 걸린 글의 모집자가 user인지 확인 — Post에 위임(Post.getUser()를 여기서 직접 만지지 않음)
    public boolean isRecruiter(User user) {
        return this.post.isOwnedBy(user);
    }
}
