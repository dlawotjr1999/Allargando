package com.wangnu.allargando.practice.controller;

import com.wangnu.allargando.global.config.SecurityConfig;
import com.wangnu.allargando.global.security.FirebaseAuthFilter;
import com.wangnu.allargando.practice.dto.PracticeLogResponseDTO;
import com.wangnu.allargando.practice.dto.PracticeLogSummaryResponseDTO;
import com.wangnu.allargando.practice.service.PracticeLogService;
import com.wangnu.allargando.user.entity.User;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PracticeLogController.class)
@Import(SecurityConfig.class)
class PracticeLogControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PracticeLogService practiceLogService;
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

        User mockUser = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now())
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
    void createPracticeLog_returns200WithRegisteredLog() throws Exception {
        PracticeLogResponseDTO response = PracticeLogResponseDTO.builder()
                .id(1L)
                .title("Bach 무반주 파르티타 1번 연습")
                .logDate(LocalDate.of(2026, 7, 4))
                .duration(90)
                .content("알레망드 첫 번째 섹션 느린 템포로 반복")
                .createdAt(LocalDateTime.of(2026, 7, 4, 12, 0))
                .build();

        when(practiceLogService.createPracticeLog(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/practice-logs")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Bach 무반주 파르티타 1번 연습",
                                  "logDate": "2026-07-04",
                                  "duration": 90,
                                  "content": "알레망드 첫 번째 섹션 느린 템포로 반복"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("Bach 무반주 파르티타 1번 연습"));
    }

    @Test
    void createPracticeLog_returns400WhenTitleMissing() throws Exception {
        mockMvc.perform(post("/api/practice-logs")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logDate": "2026-07-04",
                                  "duration": 90
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPracticeLogs_returns200WithPagedList() throws Exception {
        PracticeLogSummaryResponseDTO summary = PracticeLogSummaryResponseDTO.builder()
                .id(1L)
                .title("Bach 무반주 파르티타 1번 연습")
                .logDate(LocalDate.of(2026, 7, 4))
                .duration(90)
                .build();

        when(practiceLogService.getMyPracticeLogs(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/practice-logs")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.hasNext").exists())
                .andExpect(jsonPath("$.data.currentPage").exists());
    }

    @Test
    void getPracticeLog_returns200WithDetail() throws Exception {
        PracticeLogResponseDTO response = PracticeLogResponseDTO.builder()
                .id(1L)
                .title("Bach 무반주 파르티타 1번 연습")
                .logDate(LocalDate.of(2026, 7, 4))
                .duration(90)
                .content("알레망드 첫 번째 섹션 느린 템포로 반복")
                .createdAt(LocalDateTime.of(2026, 7, 4, 12, 0))
                .build();

        when(practiceLogService.getPracticeLog(anyLong(), any())).thenReturn(response);

        mockMvc.perform(get("/api/practice-logs/1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").value("알레망드 첫 번째 섹션 느린 템포로 반복"));
    }

    @Test
    void updatePracticeLog_returns200WithUpdatedLog() throws Exception {
        PracticeLogResponseDTO response = PracticeLogResponseDTO.builder()
                .id(1L)
                .title("수정된 제목")
                .logDate(LocalDate.of(2026, 7, 5))
                .duration(60)
                .content("수정된 내용")
                .build();

        when(practiceLogService.updatePracticeLog(anyLong(), any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/practice-logs/1")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정된 제목",
                                  "logDate": "2026-07-05",
                                  "duration": 60,
                                  "content": "수정된 내용"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));
    }

    @Test
    void deletePracticeLog_returns200() throws Exception {
        doNothing().when(practiceLogService).deletePracticeLog(anyLong(), any());

        mockMvc.perform(delete("/api/practice-logs/1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}
