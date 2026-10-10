package com.wangnu.allargando.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.wangnu.allargando.user.entity.User;

/*
 * User 저장소 — firebase_uid/nickname/email 기준 조회·중복 체크 제공
 * 닉네임은 대소문자를 구분하지 않고 비교한다(D3). V7의 lower(nickname) UNIQUE 인덱스를 타도록 lower()로 비교한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByFirebaseUid(String firebaseUid);

    @Query("select u from User u where lower(u.nickname) = lower(:nickname)")
    Optional<User> findByNicknameIgnoreCase(@Param("nickname") String nickname);

    @Query("select count(u) > 0 from User u where lower(u.nickname) = lower(:nickname)")
    boolean existsByNicknameIgnoreCase(@Param("nickname") String nickname);

    // 같은 FCM 토큰을 가진 다른 계정의 토큰을 비운다 — 토큰은 계정이 아니라 기기에 속하므로 같은 기기에서
    // 계정을 바꾸면 이전 계정에 푸시(지원 결과 등)가 남의 기기로 가지 않게 한다. 대상 행은 호출 트랜잭션에서
    // 불러오지 않은 다른 유저라 영속성 컨텍스트를 비우지 않는다(비우면 호출자가 쥔 managed 엔티티가 분리된다)
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.fcmToken = null where u.fcmToken = :fcmToken and u.id <> :userId")
    int clearFcmTokenOfOthers(@Param("fcmToken") String fcmToken, @Param("userId") Long userId);

    // FCM이 죽은 토큰(UNREGISTERED)이라고 알려 준 토큰을 모든 유저에서 비운다 — 불러오지 않은 행이라 영속성 컨텍스트를 비우지 않는다
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.fcmToken = null where u.fcmToken in :fcmTokens")
    int clearFcmTokens(@Param("fcmTokens") List<String> fcmTokens);

    boolean existsByFirebaseUid(String firebaseUid);
    boolean existsByPhoneNumber(String phoneNumber);
}
