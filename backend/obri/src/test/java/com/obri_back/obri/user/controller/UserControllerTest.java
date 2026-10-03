package com.obri_back.obri.user.controller;

import com.obri_back.obri.global.config.SecurityConfig;
import com.obri_back.obri.global.security.FirebaseAuthFilter;
import com.obri_back.obri.user.dto.UserPublicProfileDTO;
import com.obri_back.obri.user.dto.UserResponseDTO;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.service.UserService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
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

    @Test
    void getMyInfo_returns200WithUserInfo() throws Exception {
        UserResponseDTO response = UserResponseDTO.builder()
                .id(1L)
                .nickname("tester")
                .email("test@test.com")
                .phoneNumber("010-1234-5678")
                .instrument("바이올린")
                .careers(List.of())
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(userService.getMyInfo(anyLong())).thenReturn(response);

        mockMvc.perform(get("/api/users/me")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.nickname").value("tester"))
                .andExpect(jsonPath("$.data.email").value("test@test.com"));
    }

    @Test
    void getMyInfo_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다"));
    }

    @Test
    void checkNickname_returns200WithResult() throws Exception {
        when(userService.checkNickname("tester")).thenReturn(true);

        mockMvc.perform(get("/api/users/check/tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.isDuplicated").value(true));
    }

    @Test
    void getUserProfile_returns200WithUserInfo() throws Exception {
        UserPublicProfileDTO response = UserPublicProfileDTO.builder()
                .nickname("other")
                .instrument("첼로")
                .careers(List.of())
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(userService.getUserProfile("other")).thenReturn(response);

        mockMvc.perform(get("/api/users/other")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.nickname").value("other"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist());
    }

    private String careersBody(String careersJson) {
        return "{ \"nickname\": \"tester\", \"instrument\": \"바이올린\", \"careers\": " + careersJson + " }";
    }

    private static final String ONE_CAREER = "{\"organization\": \"o\", \"contexts\": \"c\"}";

    private void stubUpdateOk() {
        when(userService.updateMyInfo(any(User.class), any())).thenReturn(
                UserResponseDTO.builder().id(1L).nickname("tester").careers(List.of()).build());
    }

    private org.springframework.test.web.servlet.ResultActions putCareers(String careersJson) throws Exception {
        return mockMvc.perform(put("/api/users/me")
                .with(authentication(auth))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(careersBody(careersJson)));
    }

    // 단체명·설명이 모두 비어 있어도 요청은 통과한다(혼자 연주한 경우를 허용, 빈 행 제거는 서비스가 한다)
    @Test
    void updateMyInfo_returns200WhenCareerFieldsAreBlank() throws Exception {
        stubUpdateOk();

        putCareers("[{\"organization\": \"\", \"contexts\": \"\"}]").andExpect(status().isOk());
    }

    // 255자 초과는 DB 오류(원인과 무관한 일반 409)가 아니라 입력 검증 400으로 막는다
    @Test
    void updateMyInfo_returns400WhenCareerOrganizationTooLong() throws Exception {
        putCareers("[{\"organization\": \"" + "a".repeat(256) + "\", \"contexts\": \"c\"}]")
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateMyInfo(any(), any());
    }

    @Test
    void updateMyInfo_returns400WhenCareerContextsTooLong() throws Exception {
        putCareers("[{\"organization\": \"o\", \"contexts\": \"" + "a".repeat(256) + "\"}]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyInfo_returns200WhenCareerLengthIsExactly255() throws Exception {
        stubUpdateOk();

        putCareers("[{\"organization\": \"" + "a".repeat(255) + "\", \"contexts\": \"c\"}]")
                .andExpect(status().isOk());
    }

    // 항목 수 상한: 10개는 통과, 11개는 400
    @Test
    void updateMyInfo_returns400WhenMoreThanTenCareers() throws Exception {
        putCareers("[" + String.join(",", java.util.Collections.nCopies(11, ONE_CAREER)) + "]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMyInfo_returns200WhenExactlyTenCareers() throws Exception {
        stubUpdateOk();

        putCareers("[" + String.join(",", java.util.Collections.nCopies(10, ONE_CAREER)) + "]")
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions putBody(String body) throws Exception {
        return mockMvc.perform(put("/api/users/me")
                .with(authentication(auth))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body));
    }

    // 수정도 가입과 같이 닉네임·악기가 필수다(null·빈 문자열·공백은 400)
    @Test
    void updateMyInfo_returns400WhenNicknameMissingOrBlank() throws Exception {
        putBody("{ \"instrument\": \"바이올린\" }").andExpect(status().isBadRequest());
        putBody("{ \"nickname\": \"   \", \"instrument\": \"바이올린\" }").andExpect(status().isBadRequest());

        verify(userService, never()).updateMyInfo(any(), any());
    }

    @Test
    void updateMyInfo_returns400WhenInstrumentMissingOrBlank() throws Exception {
        putBody("{ \"nickname\": \"tester\" }").andExpect(status().isBadRequest());
        putBody("{ \"nickname\": \"tester\", \"instrument\": \"\" }").andExpect(status().isBadRequest());

        verify(userService, never()).updateMyInfo(any(), any());
    }

    // careers를 보내지 않는 것은 허용 — 경력은 미변경(닉네임·악기만 수정)
    @Test
    void updateMyInfo_returns200WhenCareersOmitted() throws Exception {
        stubUpdateOk();

        putBody("{ \"nickname\": \"tester\", \"instrument\": \"첼로\" }").andExpect(status().isOk());
    }

    @Test
    void deleteUser_returns200() throws Exception {
        doNothing().when(userService).deleteUser(any(User.class));

        mockMvc.perform(delete("/api/users/me")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}