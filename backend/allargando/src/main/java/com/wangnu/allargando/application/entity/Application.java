package com.wangnu.allargando.application.entity;

import com.wangnu.allargando.post.entity.Post;
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
    // 지원 시 보여 주는 개인정보 제3자 제공 동의 문안의 현재 버전(docs/T&C/apply-consent-screen.md). 문안을 바꿀 때만 올린다.
    // 앱이 보낸 버전이 이 값과 다르면 서버가 지원을 거절해, 옛 문안에 동의한 기록이 새 문안의 동의로 남지 않게 한다
    public static final String CONSENT_VERSION = "2026-10-15";

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

    // 개인정보 제3자 제공(이름·휴대전화번호를 수락된 모집자에게 공개) 동의 일시 — 서버가 지원을 처리한 시각을 기록한다.
    // 동의 기능 이전의 지원은 null이고, null인 지원은 수락돼도 연락처를 공개하지 않는다
    @Column(name = "third_party_consented_at")
    private LocalDateTime thirdPartyConsentedAt;

    // 동의 당시 보여 준 문안의 버전(CONSENT_VERSION)
    @Column(name = "consent_version", length = 20)
    private String consentVersion;

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
    // UNIQUE(post_id, user_id)를 건드리지 않는다. 악기·어필 문구는 새로 고른 값으로 갱신하고, 재지원 때 다시 받은
    // 제3자 제공 동의로 동의 기록도 새로 쓴다(이전 동의가 남지 않도록)
    public void reapply(String instrument, String additionalInfo, String consentVersion, LocalDateTime consentedAt) {
        this.instrument = instrument;
        this.additionalInfo = additionalInfo;
        this.status = ApplicationStatus.PENDING;
        this.consentVersion = consentVersion;
        this.thirdPartyConsentedAt = consentedAt;
    }

    // 모집자에게 지원자의 이름·전화번호를 그대로 보여 줄 수 있는지 — 수락된 지원이고 제3자 제공 동의 기록이 있을 때만.
    // 처리방침의 제공 시점("지원자가 동의한 뒤 모집자가 수락한 때")과 같은 조건이다
    public boolean exposesApplicantContact() {
        return status.exposesApplicantContact() && thirdPartyConsentedAt != null;
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
