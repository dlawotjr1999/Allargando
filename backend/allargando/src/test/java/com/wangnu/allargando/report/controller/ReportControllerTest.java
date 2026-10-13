package com.wangnu.allargando.report.controller;

import com.wangnu.allargando.global.config.SecurityConfig;
import com.wangnu.allargando.global.exception.ConflictException;
import com.wangnu.allargando.global.security.FirebaseAuthFilter;
import com.wangnu.allargando.report.dto.ReportRequestDTO;
import com.wangnu.allargando.report.entity.ReportReason;
import com.wangnu.allargando.report.service.ReportService;
import com.wangnu.allargando.user.entity.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ReportService reportService;
    @MockitoBean FirebaseAuthFilter firebaseAuthFilter;

    private Authentication auth;

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

        User mockUser = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).firebaseUid("test-uid").nickname("tester").build();
        auth = new UsernamePasswordAuthenticationToken(mockUser, null, List.of());
    }

    @Test
    void reportPost_returns200AndPassesReasonAndDetail() throws Exception {
        mockMvc.perform(post("/api/reports/posts/10")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\",\"detail\":\"광고입니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        ArgumentCaptor<ReportRequestDTO> captor = ArgumentCaptor.forClass(ReportRequestDTO.class);
        verify(reportService).reportPost(any(User.class), eq(10L), captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(captor.getValue().getDetail()).isEqualTo("광고입니다");
    }

    @Test
    void reportPost_returns400WhenReasonMissing() throws Exception {
        mockMvc.perform(post("/api/reports/posts/10")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detail\":\"사유 없음\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    void reportPost_returns400WhenReasonUnknown() throws Exception {
        mockMvc.perform(post("/api/reports/posts/10")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NOT_A_REASON\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    void reportPost_returns400WhenDetailTooLong() throws Exception {
        String longDetail = "가".repeat(501);

        mockMvc.perform(post("/api/reports/posts/10")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OTHER\",\"detail\":\"" + longDetail + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    void reportPost_returns409WhenAlreadyReported() throws Exception {
        doThrow(new ConflictException("이미 신고한 모집글입니다"))
                .when(reportService).reportPost(any(User.class), eq(10L), any(ReportRequestDTO.class));

        mockMvc.perform(post("/api/reports/posts/10")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reportUser_returns200() throws Exception {
        mockMvc.perform(post("/api/reports/users/target")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"HARASSMENT\"}"))
                .andExpect(status().isOk());

        verify(reportService).reportUser(any(User.class), eq("target"), any(ReportRequestDTO.class));
    }

    @Test
    void reportPost_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/reports/posts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }
}
