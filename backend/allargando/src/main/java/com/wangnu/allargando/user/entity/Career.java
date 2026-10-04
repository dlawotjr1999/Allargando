package com.wangnu.allargando.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 경력 엔티티 (User 종속)
 * 전용 API 없이 User 저장/수정 시 함께 처리되는 자식 컬렉션
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "career")
public class Career {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "organization")
    private String organization;

    @Column(name = "contexts")
    private String contexts;

    // 경력 생성 — register()·updateMyInfo()에서 공통으로 쓰는 DTO→엔티티 매핑 중복 제거용
    public static Career of(User user, String organization, String contexts) {
        return Career.builder()
                .user(user)
                .organization(organization)
                .contexts(contexts)
                .build();
    }
}
