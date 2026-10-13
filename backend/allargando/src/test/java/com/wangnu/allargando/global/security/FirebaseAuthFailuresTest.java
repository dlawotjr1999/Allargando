package com.wangnu.allargando.global.security;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// Firebase 검증 실패를 "토큰 문제"와 "Firebase 장애"로 가르는 규칙
class FirebaseAuthFailuresTest {

    private FirebaseAuthException failure(AuthErrorCode authCode, ErrorCode code) {
        FirebaseAuthException e = mock(FirebaseAuthException.class);
        given(e.getAuthErrorCode()).willReturn(authCode);
        org.mockito.Mockito.lenient().when(e.getErrorCode()).thenReturn(code);
        return e;
    }

    @Test
    void isServiceFailure_trueWhenCertificateFetchFailed() {
        assertThat(FirebaseAuthFailures.isServiceFailure(failure(AuthErrorCode.CERTIFICATE_FETCH_FAILED, ErrorCode.UNKNOWN))).isTrue();
    }

    @Test
    void isServiceFailure_trueWhenRemoteCallFailedWithoutAuthCode() {
        for (ErrorCode code : new ErrorCode[]{ErrorCode.UNAVAILABLE, ErrorCode.INTERNAL, ErrorCode.DEADLINE_EXCEEDED, ErrorCode.UNKNOWN}) {
            assertThat(FirebaseAuthFailures.isServiceFailure(failure(null, code))).as(code.name()).isTrue();
        }
    }

    @Test
    void isServiceFailure_falseForTokenProblems() {
        for (AuthErrorCode code : new AuthErrorCode[]{AuthErrorCode.EXPIRED_ID_TOKEN, AuthErrorCode.INVALID_ID_TOKEN,
                AuthErrorCode.REVOKED_ID_TOKEN, AuthErrorCode.USER_DISABLED, AuthErrorCode.USER_NOT_FOUND}) {
            assertThat(FirebaseAuthFailures.isServiceFailure(failure(code, ErrorCode.INVALID_ARGUMENT))).as(code.name()).isFalse();
        }
    }

    // 코드가 전혀 없는 예외는 장애로 단정하지 않는다(기존 동작인 401 유지)
    @Test
    void isServiceFailure_falseWhenNoCodesAtAll() {
        assertThat(FirebaseAuthFailures.isServiceFailure(failure(null, null))).isFalse();
    }
}
