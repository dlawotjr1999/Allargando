package com.wangnu.allargando.concert.controller;

import com.wangnu.allargando.concert.dto.ConcertResponseDTO;
import com.wangnu.allargando.concert.kopis.KopisSyncException;
import com.wangnu.allargando.concert.kopis.KopisSyncService;
import com.wangnu.allargando.concert.service.ConcertService;
import com.wangnu.allargando.global.config.SecurityConfig;
import com.wangnu.allargando.global.security.FirebaseAuthFilter;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// SecurityConfig가 /api/concerts를 화이트리스트에 두지 않아(anyRequest().authenticated()) 전 엔드포인트가
// 로그인을 요구한다 — 컨트롤러 메서드가 User를 직접 쓰지 않아도 인증 자체는 필요(PracticeLogControllerTest와 동일 패턴)
// 수동 동기화 트리거는 기본값이 꺼짐(운영)이라, 켜진 상태의 동작을 검증하려고 이 클래스에서만 켠다
@WebMvcTest(controllers = ConcertController.class, properties = "kopis.sync.manual-trigger-enabled=true")
@Import(SecurityConfig.class)
class ConcertControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ConcertService concertService;
    @MockitoBean KopisSyncService kopisSyncService;
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
                .phoneNumber("010-1234-5678")
                .nickname("tester")
                .instrument("바이올린")
                .build();

        auth = new UsernamePasswordAuthenticationToken(mockUser, null, List.of());
    }

    private ConcertResponseDTO sampleDto() {
        return ConcertResponseDTO.builder()
                .id(1L)
                .title("제92회 코리아나 챔버 뮤직 소사이어티 정기연주회")
                .category("서양음악(클래식)")
                .startDate(LocalDate.of(2026, 10, 11))
                .endDate(LocalDate.of(2026, 10, 11))
                .venue("예술의전당 [서울]")
                .region("서울특별시")
                .posterUrl("http://example.com/poster.jpg")
                .sourceUrl("https://www.kopis.or.kr/por/db/pblprfr/pblprfrView.do?menuId=MNU_00020&mt20Id=PF1")
                .build();
    }

    @Test
    void getConcertList_returns200WithPagedList() throws Exception {
        when(concertService.getConcertList(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleDto()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/concerts").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].title").value("제92회 코리아나 챔버 뮤직 소사이어티 정기연주회"))
                .andExpect(jsonPath("$.data.hasNext").exists())
                .andExpect(jsonPath("$.data.currentPage").exists());
    }

    @Test
    void getConcert_returns200WithDetail() throws Exception {
        when(concertService.getConcert(1L)).thenReturn(sampleDto());

        mockMvc.perform(get("/api/concerts/1").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.venue").value("예술의전당 [서울]"));
    }

    @Test
    void triggerSync_returns200WithSavedCount() throws Exception {
        when(kopisSyncService.sync()).thenReturn(3);

        mockMvc.perform(post("/api/concerts/sync").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.savedCount").value(3));
    }

    // 동기화 실패(잘못된 서비스키·KOPIS 장애)는 "정상 0건"이나 원인 불명 500이 아니라 502 + 사유로 알린다
    @Test
    void triggerSync_returns502WhenKopisSyncFails() throws Exception {
        when(kopisSyncService.sync())
                .thenThrow(new KopisSyncException("KOPIS 오류 응답: returncode=02, errmsg=SERVICE KEY IS NOT REGISTERED ERROR"));

        mockMvc.perform(post("/api/concerts/sync").with(authentication(auth)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("returncode=02")));
    }

    @Test
    void getConcertList_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/concerts"))
                .andExpect(status().isUnauthorized());
    }
}
