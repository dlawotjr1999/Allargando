package com.wangnu.allargando.global.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// FirebaseAuthFilter의 분기 검증 — 헤더 없음·비Bearer·빈 토큰·검증 실패·유저 없음(표식)·성공(GLB-T9, D14·D18)
@ExtendWith(MockitoExtension.class)
class FirebaseAuthFilterTest {

    @Mock FirebaseAuth firebaseAuth;
    @Mock UserRepository userRepository;
    @Mock FilterChain chain;

    FirebaseAuthFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new FirebaseAuthFilter(firebaseAuth, userRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 어떤 경우든 예외 없이 다음 필터로 진행한다
    private void run() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void noHeader_passesWithoutAuthentication() throws Exception {
        run();
        verifyNoInteractions(firebaseAuth);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerHeader_passesWithoutVerification() throws Exception {
        request.addHeader("Authorization", "Basic abc");
        run();
        verifyNoInteractions(firebaseAuth);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankToken_passesWithoutVerification() throws Exception {
        request.addHeader("Authorization", "Bearer ");
        run();
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void invalidToken_passesWithoutAuthenticationOrMarker() throws Exception {
        request.addHeader("Authorization", "Bearer bad");
        given(firebaseAuth.verifyIdToken("bad")).willThrow(mock(FirebaseAuthException.class));
        run();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(FirebaseAuthFilter.UNREGISTERED_USER_ATTRIBUTE)).isNull();
    }

    // 토큰은 유효한데 가입된 유저가 없으면 인증은 비운 채 표식을 남긴다 → 진입점이 404로 응답
    @Test
    void validTokenButNoUser_marksRequestAsUnregistered() throws Exception {
        request.addHeader("Authorization", "Bearer good");
        FirebaseToken token = mock(FirebaseToken.class);
        given(token.getUid()).willReturn("uid-1");
        given(firebaseAuth.verifyIdToken("good")).willReturn(token);
        given(userRepository.findByFirebaseUid("uid-1")).willReturn(Optional.empty());
        run();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(FirebaseAuthFilter.UNREGISTERED_USER_ATTRIBUTE)).isEqualTo(true);
    }

    @Test
    void validTokenAndUser_setsAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer good");
        FirebaseToken token = mock(FirebaseToken.class);
        given(token.getUid()).willReturn("uid-1");
        given(firebaseAuth.verifyIdToken("good")).willReturn(token);
        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();
        given(userRepository.findByFirebaseUid("uid-1")).willReturn(Optional.of(user));
        run();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(user);
        assertThat(request.getAttribute(FirebaseAuthFilter.UNREGISTERED_USER_ATTRIBUTE)).isNull();
    }

    // Firebase 공개키를 가져오지 못한 것은 토큰이 틀린 것이 아니다 → 표식을 남겨 진입점이 503으로 응답하게 한다
    @Test
    void firebaseOutage_marksRequestAsAuthUnavailable() throws Exception {
        request.addHeader("Authorization", "Bearer good");
        FirebaseAuthException outage = mock(FirebaseAuthException.class);
        given(outage.getAuthErrorCode()).willReturn(com.google.firebase.auth.AuthErrorCode.CERTIFICATE_FETCH_FAILED);
        given(firebaseAuth.verifyIdToken("good")).willThrow(outage);
        run();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(FirebaseAuthFilter.AUTH_UNAVAILABLE_ATTRIBUTE)).isEqualTo(true);
    }

    // 만료된 토큰은 토큰 문제다 → 표식 없이 401로 간다
    @Test
    void expiredToken_isNotMarkedAsAuthUnavailable() throws Exception {
        request.addHeader("Authorization", "Bearer old");
        FirebaseAuthException expired = mock(FirebaseAuthException.class);
        given(expired.getAuthErrorCode()).willReturn(com.google.firebase.auth.AuthErrorCode.EXPIRED_ID_TOKEN);
        given(firebaseAuth.verifyIdToken("old")).willThrow(expired);
        run();
        assertThat(request.getAttribute(FirebaseAuthFilter.AUTH_UNAVAILABLE_ATTRIBUTE)).isNull();
    }

    // DB 장애로 유저를 조회하지 못해도 필터가 예외를 던지지 않고 표식을 남긴다(비 JSON 오류 방지)
    @Test
    void databaseFailure_marksRequestAsAuthUnavailable() throws Exception {
        request.addHeader("Authorization", "Bearer good");
        FirebaseToken token = mock(FirebaseToken.class);
        given(token.getUid()).willReturn("uid-1");
        given(firebaseAuth.verifyIdToken("good")).willReturn(token);
        given(userRepository.findByFirebaseUid("uid-1"))
                .willThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));
        run();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(FirebaseAuthFilter.AUTH_UNAVAILABLE_ATTRIBUTE)).isEqualTo(true);
    }
}
