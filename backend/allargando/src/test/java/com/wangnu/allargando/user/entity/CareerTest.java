package com.wangnu.allargando.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerTest {

    @Test
    void of_buildsCareerBoundToUser() {
        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).nickname("tester").firebaseUid("test-uid").build();

        Career career = Career.of(user, "서울시향", "2023년 객원 연주");

        assertThat(career.getUser()).isEqualTo(user);
        assertThat(career.getOrganization()).isEqualTo("서울시향");
        assertThat(career.getContexts()).isEqualTo("2023년 객원 연주");
    }
}
