package com.obri_back.obri.auth.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.obri_back.obri.auth.service.AuthService;
import com.obri_back.obri.global.config.SecurityConfig;
import com.obri_back.obri.global.security.FirebaseAuthFilter;
import com.obri_back.obri.auth.dto.RegisterResponseDTO;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;

    @TestConfiguration
    static class FilterPassThroughConfig {
        @Bean
        FirebaseAuthFilter firebaseAuthFilter() {
            return new FirebaseAuthFilter(
                    Mockito.mock(FirebaseAuth.class),
                    Mockito.mock(UserRepository.class)) {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain)
                        throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }

    private Authentication auth;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .email("test@test.com")
                .firebaseUid("test-uid")
                .phoneNumber("010-1234-5678")
                .nickname("tester")
                .instrument("바이올린")
                .build();

        auth = new UsernamePasswordAuthenticationToken(mockUser, null, List.of());
    }

    @Test
    void register_returns200WithUserInfo() throws Exception {
        RegisterResponseDTO response = RegisterResponseDTO.builder()
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(authService.register(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "tester",
                                  "instrument": "바이올린",
                                  "careers": [
                                    { "organization": "서울시향", "contexts": "2023년 객원 연주" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다"))
                .andExpect(jsonPath("$.data.createdAt").value("2024-01-01T00:00:00"))
                .andExpect(jsonPath("$.data.nickname").doesNotExist());
    }

    // register는 permitAll이라 미인증 상태로도 호출된다 — 헤더가 "Bearer " 형식이 아니면
    // substring(7)에서 StringIndexOutOfBounds가 나 500이 되던 것을 401로 차단(CLAUDE.md §7)
    @Test
    void register_returns401WhenAuthorizationHeaderMalformed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "테스터",
                                  "instrument": "바이올린"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(authService, never()).register(any(), any());
    }

    // 헤더 누락도 형식 오류와 같은 401 — 클라이언트가 "인증 정보 문제"를 한 가지로 처리할 수 있다
    @Test
    void register_returns401WhenAuthorizationHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"nickname\": \"tester\", \"instrument\": \"바이올린\" }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(authService, never()).register(any(), any());
    }

    @Test
    void updatePhoneNumber_returns401WhenAuthorizationHeaderMissing() throws Exception {
        mockMvc.perform(patch("/api/auth/phone-number")
                        .with(authentication(auth)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(authService, never()).updatePhoneNumber(any(), any());
    }

    @Test
    void register_returns401WhenBearerTokenEmpty() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "테스터",
                                  "instrument": "바이올린"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(authService, never()).register(any(), any());
    }

    @Test
    void register_returns400WhenNicknameMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrument": "바이올린"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400WhenInstrumentMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "tester"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions postRegisterWithCareers(String careersJson) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"nickname\": \"tester\", \"instrument\": \"바이올린\", \"careers\": " + careersJson + " }"));
    }

    // 가입 요청의 경력도 수정과 같은 검증을 받는다: 255자 초과·11개 초과는 400, 빈 값은 허용
    @Test
    void register_returns400WhenCareerTooLong() throws Exception {
        postRegisterWithCareers("[{\"organization\": \"" + "a".repeat(256) + "\", \"contexts\": \"c\"}]")
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(), any());
    }

    @Test
    void register_returns400WhenMoreThanTenCareers() throws Exception {
        String one = "{\"organization\": \"o\", \"contexts\": \"c\"}";

        postRegisterWithCareers("[" + String.join(",", java.util.Collections.nCopies(11, one)) + "]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns200WhenCareerFieldsAreBlank() throws Exception {
        when(authService.register(any(), any())).thenReturn(
                RegisterResponseDTO.builder().createdAt(LocalDateTime.of(2024, 1, 1, 0, 0)).build());

        postRegisterWithCareers("[{\"organization\": \"\", \"contexts\": \"\"}]")
                .andExpect(status().isOk());
    }

    // 잘못된 메서드·Content-Type·경로가 500 + ERROR 스택트레이스가 되지 않는다(register는 permitAll이라 비인증으로도 재현됨)
    @Test
    void register_returns405WhenMethodNotSupported() throws Exception {
        mockMvc.perform(get("/api/auth/register"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void register_returns415WhenContentTypeNotSupported() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("nickname=tester"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void unknownPath_returns404WhenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateFcmToken_returns200() throws Exception {
        mockMvc.perform(patch("/api/auth/fcm-token")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fcmToken": "test_fcm_token_12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("FCM 토큰이 갱신되었습니다"));
    }

    @Test
    void clearFcmToken_returns200() throws Exception {
        mockMvc.perform(delete("/api/auth/fcm-token").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("FCM 토큰이 해제되었습니다"));
    }

    @Test
    void updatePhoneNumber_returns200() throws Exception {
        mockMvc.perform(patch("/api/auth/phone-number")
                        .with(authentication(auth))
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("전화번호가 변경되었습니다"));

        verify(authService).updatePhoneNumber(mockUser, "test-token");
    }
}
