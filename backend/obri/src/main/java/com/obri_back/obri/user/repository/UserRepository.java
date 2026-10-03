package com.obri_back.obri.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.obri_back.obri.user.entity.User;

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

    boolean existsByFirebaseUid(String firebaseUid);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}
