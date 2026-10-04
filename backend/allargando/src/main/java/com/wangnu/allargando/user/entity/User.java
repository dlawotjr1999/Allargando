package com.wangnu.allargando.user.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/*
 * 유저 엔티티
 * 내부 식별자(id)와 Firebase 외부 식별자(firebaseUid)를 분리 관리하며,
 * 계정 고유성 앵커는 phoneNumber(UNIQUE)·firebaseUid(UNIQUE). Career와 1:N 소유
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false, unique = true)
    private Long id;

    // 이메일 UNIQUE: 계정당 유일. null 허용(전화 인증 등 email 부재 케이스) — PostgreSQL도 UNIQUE에서 NULL끼리는 서로 다른 값으로 취급해 다중 NULL 허용
    @Column(name = "email", unique = true)
    private String email;

    // Firebase가 계정마다 발급하는 전역 유일 식별자. 조회 키이므로 UNIQUE는 정합성 필수 조건
    @Column(name = "firebase_uid", nullable = false, unique = true)
    private String firebaseUid;

    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    // 대소문자 무시 UNIQUE는 함수 인덱스라 JPA로 표현할 수 없다 — Flyway V7의 UK_user_nickname_lower(lower(nickname)).
    // 형식·예약어 규칙은 NicknamePolicy(D3)
    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "instrument", nullable = false)
    private String instrument;

    @Column(name = "fcm_token")
    private String fcmToken;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // User-Career 양방향 1:N (부모 저장/수정 시 cascade·orphanRemoval로 함께 처리)
    // @BatchSize: 지원자 목록 등에서 여러 User의 careers를 순회 조회할 때 N+1 방지
    @Builder.Default
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY,
        cascade = CascadeType.ALL, orphanRemoval = true
    )
    private List<Career> careers = new ArrayList<>();

    // FCM 토큰 갱신 (앱 실행 시 최신 토큰 반영)
    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    // 전화번호 갱신 (PATCH /api/auth/phone-number — Firebase ID Token의 phone_number claim만 신뢰)
    public void updatePhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    // 내 정보 수정: 닉네임·악기를 그대로 반영 (둘 다 필수 입력이라 호출 전에 요청 검증이 끝난 값이다.
    // phoneNumber는 전용 인증 엔드포인트로 이관되어 여기서 다루지 않음)
    public void updateInfo(String nickname, String instrument) {
        this.nickname = nickname;
        this.instrument = instrument;
    }
}
