package com.obri_back.obri.post.controller;

import com.obri_back.obri.global.config.SecurityConfig;
import com.obri_back.obri.global.security.FirebaseAuthFilter;
import com.obri_back.obri.post.dto.PostDetailResponseDTO;
import com.obri_back.obri.post.dto.PostInstrumentDTO;
import com.obri_back.obri.post.dto.PostResponseDTO;
import com.obri_back.obri.post.dto.PostSummaryResponseDTO;
import com.obri_back.obri.post.entity.PostStatus;
import com.obri_back.obri.post.service.PostService;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
class PostControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PostService postService;
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
    void createPost_returns200WithRegisteredPost() throws Exception {
        PostResponseDTO response = PostResponseDTO.builder()
                .id(1L)
                .category("앙상블")
                .title("현악 앙상블 단원 모집")
                .eventAt(LocalDateTime.of(2024, 5, 1, 14, 0))
                .location("서울 강남구 OO스튜디오")
                .timetable("매주 토요일 오후 2시 합주")
                .status(PostStatus.OPEN)
                .instruments(List.of(
                        PostInstrumentDTO.builder().instrument("바이올린").people(2).confirmed(0).closed(false).build()
                ))
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(postService.createPost(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/posts")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "앙상블",
                                  "title": "현악 앙상블 단원 모집",
                                  "eventAt": "2099-05-01T14:00:00",
                                  "location": "서울 강남구 OO스튜디오",
                                  "region": "서울",
                                  "timetable": "매주 토요일 오후 2시 합주",
                                  "instruments": [
                                    { "instrument": "바이올린", "people": 2 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("모집글이 등록되었습니다"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("현악 앙상블 단원 모집"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void createPost_returns400WhenTitleMissing() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "앙상블",
                                  "eventAt": "2099-05-01T14:00:00",
                                  "location": "서울 강남구 OO스튜디오",
                                  "timetable": "매주 토요일 오후 2시 합주",
                                  "instruments": [{ "instrument": "바이올린", "people": 2 }]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // 2026-08-17 발견된 버그 회귀 방지: 중복 악기명이 등록되면 이후 수정 시 replaceInstruments가
    // Duplicate key 예외를 던지던 버그의 유입 경로 차단(등록 시점 400)
    @Test
    void createPost_returns400WhenInstrumentNameDuplicated() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "앙상블",
                                  "title": "현악 앙상블 단원 모집",
                                  "eventAt": "2099-05-01T14:00:00",
                                  "location": "서울 강남구 OO스튜디오",
                                  "region": "서울",
                                  "timetable": "매주 토요일 오후 2시 합주",
                                  "instruments": [
                                    { "instrument": "바이올린", "people": 2 },
                                    { "instrument": "바이올린", "people": 3 }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // 정렬은 서버가 고정한다 — ?sort=로 임의 속성(연관 엔티티의 개인정보 컬럼 등)을 지정해도 반영되지 않고,
    // 페이지 크기는 설정한 상한(50)으로 잘린다
    @Test
    void getPosts_ignoresClientSortAndCapsPageSize() throws Exception {
        when(postService.getPosts(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/posts?sort=user.phoneNumber,asc&size=100000")
                        .with(authentication(auth)))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).getPosts(any(), any(), any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void getPosts_returns200WithPagedList() throws Exception {
        PostSummaryResponseDTO summary = PostSummaryResponseDTO.builder()
                .id(1L)
                .title("현악 앙상블 단원 모집")
                .category("앙상블")
                .eventAt(LocalDateTime.of(2024, 5, 1, 14, 0))
                .location("서울 강남구 OO스튜디오")
                .instruments(List.of(
                        PostInstrumentDTO.builder().instrument("바이올린").people(2).confirmed(0).closed(false).build()
                ))
                .timetable("매주 토요일 오후 2시 합주")
                .status(PostStatus.OPEN)
                .build();

        when(postService.getPosts(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/posts")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.hasNext").exists())
                .andExpect(jsonPath("$.data.currentPage").exists());
    }

    @Test
    void getMyPosts_returns200WithPagedList() throws Exception {
        PostSummaryResponseDTO summary = PostSummaryResponseDTO.builder()
                .id(1L)
                .title("현악 앙상블 단원 모집")
                .category("앙상블")
                .eventAt(LocalDateTime.of(2024, 5, 1, 14, 0))
                .location("서울 강남구 OO스튜디오")
                .instruments(List.of(
                        PostInstrumentDTO.builder().instrument("바이올린").people(2).confirmed(0).closed(false).build()
                ))
                .timetable("매주 토요일 오후 2시 합주")
                .status(PostStatus.OPEN)
                .build();

        when(postService.getMyPosts(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/posts/me")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.hasNext").exists())
                .andExpect(jsonPath("$.data.currentPage").exists());
    }

    @Test
    void getPost_returns200WithDetail() throws Exception {
        PostDetailResponseDTO response = PostDetailResponseDTO.builder()
                .id(1L)
                .writer(PostDetailResponseDTO.Writer.builder().nickname("홍길동").instrument("바이올린").build())
                .applicationCount(3L)
                .isMine(false)
                .hasApplied(false)
                .category("앙상블")
                .title("현악 앙상블 단원 모집")
                .eventAt(LocalDateTime.of(2024, 5, 1, 14, 0))
                .location("서울 강남구 OO스튜디오")
                .timetable("매주 토요일 오후 2시 합주")
                .status(PostStatus.OPEN)
                .instruments(List.of(
                        PostInstrumentDTO.builder().instrument("바이올린").people(2).confirmed(0).closed(false).build()
                ))
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(postService.getPost(anyLong(), any())).thenReturn(response);

        mockMvc.perform(get("/api/posts/1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.applicationCount").value(3))
                .andExpect(jsonPath("$.data.writer.nickname").value("홍길동"));
    }

    @Test
    void updatePost_returns200WithUpdatedPost() throws Exception {
        PostResponseDTO response = PostResponseDTO.builder()
                .id(1L)
                .category("앙상블")
                .title("수정된 제목")
                .eventAt(LocalDateTime.of(2024, 5, 1, 15, 0))
                .location("서울 강남구 OO스튜디오")
                .timetable("매주 토요일 오후 3시 합주")
                .status(PostStatus.OPEN)
                .instruments(List.of(
                        PostInstrumentDTO.builder().instrument("바이올린").people(3).confirmed(0).closed(false).build()
                ))
                .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build();

        when(postService.updatePost(anyLong(), any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/posts/1")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "앙상블",
                                  "title": "수정된 제목",
                                  "eventAt": "2099-05-01T15:00:00",
                                  "location": "서울 강남구 OO스튜디오",
                                  "region": "서울",
                                  "timetable": "매주 토요일 오후 3시 합주",
                                  "instruments": [
                                    { "instrument": "바이올린", "people": 3 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.title").value("수정된 제목"));
    }

    @Test
    void closePost_returns200() throws Exception {
        doNothing().when(postService).closePost(anyLong(), any());

        mockMvc.perform(patch("/api/posts/1/close")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("모집글이 마감되었습니다"));
    }

    @Test
    void deletePost_returns200() throws Exception {
        doNothing().when(postService).deletePost(anyLong(), any());

        mockMvc.perform(delete("/api/posts/1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("모집글이 삭제되었습니다"));
    }

    // POST-T3: 요청 검증이 DB 오류(409)가 아니라 400 + 한글 메시지로 먼저 막는다
    private org.springframework.test.web.servlet.ResultActions postWith(String eventAt, String title, String people)
            throws Exception {
        return mockMvc.perform(post("/api/posts")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "category": "앙상블", "title": "%s", "eventAt": "%s",
                          "location": "서울", "region": "서울", "timetable": "토요일",
                          "instruments": [{"instrument": "바이올린", "people": %s}]
                        }
                        """.formatted(title, eventAt, people)));
    }

    @Test
    void createPost_returns400WhenTitleLongerThan255() throws Exception {
        postWith("2099-05-01T14:00:00", "가".repeat(256), "2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: 제목은 255자 이내여야 합니다"));
    }

    @Test
    void createPost_returns400WhenEventAtInThePast() throws Exception {
        postWith("2020-05-01T14:00:00", "제목", "2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("eventAt: 공연 일시는 현재 이후여야 합니다"));
    }

    @Test
    void createPost_returns400WhenPeopleOverLimit() throws Exception {
        postWith("2099-05-01T14:00:00", "제목", "101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("instruments[0].people: 모집 인원은 100명 이하여야 합니다"));
    }
}
