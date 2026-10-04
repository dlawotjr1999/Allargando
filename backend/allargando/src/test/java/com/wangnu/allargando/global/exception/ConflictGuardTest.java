package com.wangnu.allargando.global.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictGuardTest {

    @Test
    void requireUnique_throwsConflictExceptionWhenAlreadyExists() {
        assertThatThrownBy(() -> ConflictGuard.requireUnique(true, "이미 사용 중입니다"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 사용 중입니다");
    }

    @Test
    void requireUnique_doesNothingWhenNotExists() {
        assertThatCode(() -> ConflictGuard.requireUnique(false, "이미 사용 중입니다"))
                .doesNotThrowAnyException();
    }
}
