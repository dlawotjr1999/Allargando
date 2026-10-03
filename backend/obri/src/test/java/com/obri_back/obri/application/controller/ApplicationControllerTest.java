package com.obri_back.obri.application.controller;

import com.obri_back.obri.application.dto.AppResponseDTO;
import com.obri_back.obri.application.entity.ApplicationStatus;
import com.obri_back.obri.application.service.ApplicationService;
import com.obri_back.obri.global.config.SecurityConfig;
import com.obri_back.obri.global.security.FirebaseAuthFilter;
import com.obri_back.obri.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApplicationController.class)
@Import(SecurityConfig.class)
class ApplicationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ApplicationService applicationService;
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

        User mockUser = User.builder()
                .id(1L)
                .email("test@test.com")
                .firebaseUid("test-uid")
                .nickname("tester")
                .instrument("바이올린")
                .build();

        auth = new UsernamePasswordAuthenticationToken(mockUser, null, List.of());
    }

    @Test
    void submitApplication_returns200() throws Exception {
        AppResponseDTO response = AppResponseDTO.builder()
                .id(1L)
                .additionalInfo("추가 정보")
                .status(ApplicationStatus.PENDING)
                .build();

        when(applicationService.submitApplication(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/applications/submit")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 10,
                                  "additionalInfo": "추가 정보"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // postId 누락 시 findById(null) → 500 대신 400으로 사전 차단
    @Test
    void submitApplication_returns400WhenPostIdMissing() throws Exception {
        mockMvc.perform(post("/api/applications/submit")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "additionalInfo": "추가 정보"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(applicationService, never()).submitApplication(any(), any());
    }

    @Test
    void getMyApplications_returns200WithPagedList() throws Exception {
        AppResponseDTO response = AppResponseDTO.builder()
                .id(1L)
                .status(ApplicationStatus.PENDING)
                .build();

        when(applicationService.getApplicationsByUserId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/applications/me")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.hasNext").exists());
    }

    @Test
    void acceptApplication_returns200() throws Exception {
        doNothing().when(applicationService).accept(any(), any());

        mockMvc.perform(patch("/api/applications/1/accept")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("지원을 수락했습니다"));
    }

    @Test
    void rejectApplication_returns200() throws Exception {
        doNothing().when(applicationService).reject(any(), any());

        mockMvc.perform(patch("/api/applications/1/reject")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("지원을 거절했습니다"));
    }

    @Test
    void cancelApplication_returns200() throws Exception {
        doNothing().when(applicationService).cancel(any(), any());

        mockMvc.perform(patch("/api/applications/1/cancel")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("지원을 취소했습니다"));
    }

    @Test
    void revokeApplication_returns200() throws Exception {
        doNothing().when(applicationService).revoke(any(), any());

        mockMvc.perform(patch("/api/applications/1/revoke")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("수락을 철회했습니다"));
    }
}