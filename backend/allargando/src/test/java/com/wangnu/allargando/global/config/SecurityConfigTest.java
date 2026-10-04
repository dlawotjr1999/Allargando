package com.wangnu.allargando.global.config;

import com.wangnu.allargando.global.security.FirebaseAuthFilter;
import com.wangnu.allargando.user.controller.UserController;
import com.wangnu.allargando.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

// BACKLOG.md #36: 네이티브 앱 런타임은 CORS 대상이 아니라, 로컬에서 Expo web(브라우저)으로 붙는 경우에만 필요.
// app.cors.allowed-origins로 화이트리스트에 있는 오리진만 허용되는지 검증(permitAll 라우트로 인증 없이 확인)
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:8081")
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccessDeniedHandler accessDeniedHandler;
    @Autowired org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint;
    @MockitoBean UserService userService;
    @MockitoBean FirebaseAuthFilter firebaseAuthFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2, FilterChain.class);
            chain.doFilter(
                invocation.getArgument(0, ServletRequest.class),
                invocation.getArgument(1, ServletResponse.class)
            );
            return null;
        }).when(firebaseAuthFilter).doFilter(any(), any(), any());
    }

    @Test
    void allowsWhitelistedOrigin() throws Exception {
        mockMvc.perform(get("/api/users/check/tester").header("Origin", "http://localhost:8081"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"));
    }

    @Test
    void rejectsNonWhitelistedOrigin() throws Exception {
        mockMvc.perform(get("/api/users/check/tester").header("Origin", "https://evil.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // 토큰은 유효한데 DB에 유저가 없으면(필터가 표식을 남김) 401이 아니라 404 — 클라이언트가 가입 미완료를 구분한다(D18)
    @Test
    void entryPoint_returnsNotFoundWhenTokenValidButUserUnregistered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(FirebaseAuthFilter.UNREGISTERED_USER_ATTRIBUTE, true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(request, response,
                new org.springframework.security.authentication.InsufficientAuthenticationException("x"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"status\":404").contains("가입되지 않은 사용자입니다");
    }

    @Test
    void entryPoint_returnsUnauthorizedOtherwise() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(new MockHttpServletRequest(), response,
                new org.springframework.security.authentication.InsufficientAuthenticationException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // 기본 AccessDeniedHandler는 403 + 빈 바디라 클라이언트가 응답을 파싱하지 못한다 — APIResponse 형식으로 응답
    @Test
    void accessDeniedHandler_writesForbiddenApiResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"status\":403").contains("접근 권한이 없습니다");
    }
}
