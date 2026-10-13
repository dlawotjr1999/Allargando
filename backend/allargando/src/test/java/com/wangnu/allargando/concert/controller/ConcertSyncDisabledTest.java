package com.wangnu.allargando.concert.controller;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영 기본값(kopis.sync.manual-trigger-enabled=false)에서 수동 동기화 트리거가 막히는지 검증.
// 로컬에서는 application-local.properties가 이 값을 true로 덮어쓰므로 false를 명시한다.
// 프로퍼티 값이 달라 ConcertControllerTest(켜진 상태)와 컨텍스트를 공유할 수 없어 클래스를 분리
@WebMvcTest(controllers = ConcertController.class, properties = "kopis.sync.manual-trigger-enabled=false")
@Import(SecurityConfig.class)
class ConcertSyncDisabledTest {

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

        User mockUser = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now())
                .id(1L)
                .firebaseUid("test-uid")
                .phoneNumber("010-1234-5678")
                .nickname("tester")
                .instrument("바이올린")
                .build();

        auth = new UsernamePasswordAuthenticationToken(mockUser, null, List.of());
    }

    @Test
    void triggerSync_returns404WhenManualTriggerDisabled() throws Exception {
        mockMvc.perform(post("/api/concerts/sync").with(authentication(auth)))
                .andExpect(status().isNotFound());

        verify(kopisSyncService, never()).sync();
    }
}
