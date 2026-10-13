package com.wangnu.allargando.global.security;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;

/*
 * Firebase 토큰 검증 실패가 "토큰이 틀림"인지 "Firebase에 닿지 못함"인지 가른다.
 * 둘을 같은 401로 내보내면 Firebase가 잠깐 불안정할 때 접속 중인 사용자가 모두 로그아웃되고(앱은 401에서 로그아웃),
 * 서버 로그로도 원인을 알 수 없다. 필터(FirebaseAuthFilter)와 AuthService.verifyToken이 같이 쓴다.
 */
public final class FirebaseAuthFailures {

    public static final String UNAVAILABLE_MESSAGE = "인증 서버에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해주세요";

    private FirebaseAuthFailures() {
    }

    /*
     * Firebase 쪽 장애로 토큰을 판정하지 못한 경우인가.
     *   - 공개키(인증서) 조회 실패: AuthErrorCode.CERTIFICATE_FETCH_FAILED
     *   - 폐기 확인 등 원격 호출 실패: 인증 오류 코드 없이 UNAVAILABLE·INTERNAL·DEADLINE_EXCEEDED·UNKNOWN
     * 만료·위조·폐기·비활성 계정처럼 인증 오류 코드가 붙은 실패는 토큰 문제라 false다.
     */
    public static boolean isServiceFailure(FirebaseAuthException e) {
        AuthErrorCode authCode = e.getAuthErrorCode();
        if (authCode == AuthErrorCode.CERTIFICATE_FETCH_FAILED) {
            return true;
        }
        if (authCode != null) {
            return false;
        }
        ErrorCode code = e.getErrorCode();
        return code == ErrorCode.UNAVAILABLE || code == ErrorCode.INTERNAL
                || code == ErrorCode.DEADLINE_EXCEEDED || code == ErrorCode.UNKNOWN;
    }

    // 로그용 요약 — 오류 코드만 남긴다(토큰·예외 메시지는 남기지 않는다)
    public static String describe(FirebaseAuthException e) {
        return "authErrorCode=" + e.getAuthErrorCode() + ", errorCode=" + e.getErrorCode();
    }
}
