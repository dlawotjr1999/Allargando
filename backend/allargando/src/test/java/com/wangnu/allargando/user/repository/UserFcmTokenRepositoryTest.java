package com.wangnu.allargando.user.repository;

import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// clearFcmTokenOfOthers JPQL을 실제로 실행해 검증한다(H2) — 같은 토큰을 쥔 다른 계정만 비우고 본인·다른 토큰은 건드리지 않는다
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserFcmTokenRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired TestEntityManager em;

    private User save(String key, String token) {
        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).firebaseUid("uid-" + key).phoneNumber("p-" + key)
                .nickname("n_" + key).instrument("바이올린").build();
        user.updateFcmToken(token);
        return em.persistAndFlush(user);
    }

    @Test
    void clearFcmTokenOfOthers_clearsOnlyOtherAccountsWithSameToken() {
        User me = save("me", "device-token");
        User previous = save("prev", "device-token");
        User unrelated = save("other", "another-token");

        int cleared = userRepository.clearFcmTokenOfOthers("device-token", me.getId());
        em.clear();

        assertThat(cleared).isEqualTo(1);
        assertThat(userRepository.findById(previous.getId()).orElseThrow().getFcmToken()).isNull();
        assertThat(userRepository.findById(me.getId()).orElseThrow().getFcmToken()).isEqualTo("device-token");
        assertThat(userRepository.findById(unrelated.getId()).orElseThrow().getFcmToken()).isEqualTo("another-token");
    }
}
