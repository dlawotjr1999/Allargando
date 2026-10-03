package com.obri_back.obri.block.controller;

import com.obri_back.obri.block.dto.BlockedUserResponseDTO;
import com.obri_back.obri.block.service.BlockService;
import com.obri_back.obri.global.config.SecurityConfig;
import com.obri_back.obri.global.exception.ConflictException;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BlockController.class)
@Import(SecurityConfig.class)
class BlockControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean BlockService blockService;
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

        User mockUser = User.builder().id(1L).firebaseUid("test-uid").nickname("tester").build();
        auth = new UsernamePasswordAuthenticationToken(mockUser, null, List.of());
    }

    @Test
    void block_returns200() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"target\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(blockService).block(any(User.class), eq("target"));
    }

    @Test
    void block_returns400WhenNicknameBlank() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(blockService);
    }

    @Test
    void block_returns409WhenAlreadyBlocked() throws Exception {
        doThrow(new ConflictException("이미 차단한 사용자입니다"))
                .when(blockService).block(any(User.class), eq("target"));

        mockMvc.perform(post("/api/blocks")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"target\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getMyBlocks_returns200WithList() throws Exception {
        when(blockService.getMyBlocks(1L)).thenReturn(List.of(
                BlockedUserResponseDTO.builder().nickname("target").instrument("첼로")
                        .blockedAt(LocalDateTime.of(2026, 10, 2, 12, 0)).build()));

        mockMvc.perform(get("/api/blocks").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("target"))
                .andExpect(jsonPath("$.data[0].instrument").value("첼로"));
    }

    @Test
    void unblock_returns200() throws Exception {
        mockMvc.perform(delete("/api/blocks/target").with(authentication(auth)))
                .andExpect(status().isOk());

        verify(blockService).unblock(any(User.class), eq("target"));
    }

    @Test
    void getMyBlocks_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/blocks"))
                .andExpect(status().isUnauthorized());
    }
}
