package com.wangnu.allargando.global;

import com.google.firebase.messaging.FirebaseMessaging;
import com.wangnu.allargando.application.entity.Application;
import com.wangnu.allargando.application.entity.ApplicationStatus;
import com.wangnu.allargando.application.repository.ApplicationRepository;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.post.repository.PostRepository;
import com.wangnu.allargando.user.entity.Career;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.CareerRepository;
import com.wangnu.allargando.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * 읽기 API가 실제 컨텍스트·PostgreSQL에서 지연 로딩 오류 없이 200을 주는지 훑는 스모크 테스트.
 * open-in-view를 끈 운영 설정(GLB-T15)에서는 컨트롤러·DTO 변환이 트랜잭션 밖에서 LAZY 연관에 닿으면
 * LazyInitializationException이 난다 — 서비스 단위 테스트(Mockito)와 @WebMvcTest(서비스 목)로는 잡히지 않으므로
 * 실제 서비스·리포지토리를 태운다. 인증 주체는 필터와 같이 DB에서 새로 읽은(분리된) User를 쓴다.
 * 접속 정보·정리 방식은 다른 *PostgresTest와 같다(환경변수 SPRING_DATASOURCE_*, 테스트가 만든 uid만 삭제).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReadApiSmokePostgresTest {

    private static final String PREFIX = "it-smoke-";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;
    @Autowired PostRepository postRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean FirebaseMessaging firebaseMessaging; // 알림 발송은 시험 대상이 아니다

    private Long postId;
    private Long applicationId;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        String ours = "(select user_id from \"user\" where firebase_uid like '" + PREFIX + "%')";
        jdbcTemplate.update("delete from application where user_id in " + ours);
        jdbcTemplate.update("delete from post_instrument where post_id in (select post_id from post where user_id in " + ours + ")");
        jdbcTemplate.update("delete from post where user_id in " + ours);
        jdbcTemplate.update("delete from career where user_id in " + ours);
        jdbcTemplate.update("delete from \"user\" where firebase_uid like '" + PREFIX + "%'");
    }

    private User save(String key) {
        return userRepository.save(User.builder().firebaseUid(PREFIX + key).phoneNumber(PREFIX + "p-" + key)
                .nickname("smoke_" + key).instrument("바이올린").build());
    }

    private void seed() {
        User owner = save("owner");
        User applicant = save("applicant");
        careerRepository.save(Career.of(applicant, "동호회", "객원"));
        Post post = Post.create(owner, PostInfo.builder().category("앙상블").title("스모크")
                .eventAt(LocalDateTime.now().plusDays(7)).location("서울").region("서울").timetable("13:00").build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 2));
        postId = postRepository.save(post).getId();
        applicationId = applicationRepository.save(Application.builder().user(applicant).post(post)
                .instrument("바이올린").status(ApplicationStatus.PENDING).build()).getId();
    }

    // 필터가 하듯 DB에서 새로 읽은 User — careers 같은 LAZY 컬렉션이 초기화되지 않은 분리 상태
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String key) {
        User detached = userRepository.findByFirebaseUid(PREFIX + key).orElseThrow();
        return authentication(new UsernamePasswordAuthenticationToken(detached, null, Collections.emptyList()));
    }

    @Test
    void ownerReadEndpoints_returnOkWithoutLazyLoadingErrors() throws Exception {
        seed();

        mockMvc.perform(get("/api/posts").with(as("owner"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].instruments").isArray());
        mockMvc.perform(get("/api/posts/me").with(as("owner"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/" + postId).with(as("owner"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMine").value(true));
        mockMvc.perform(get("/api/applications/post/" + postId).with(as("owner"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].applicant.careers[0].organization").value("동호회"));
        mockMvc.perform(get("/api/users/me").with(as("owner"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/blocks").with(as("owner"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/practice-logs").with(as("owner"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/concerts").with(as("owner"))).andExpect(status().isOk());
    }

    @Test
    void applicantReadEndpoints_returnOkWithoutLazyLoadingErrors() throws Exception {
        seed();

        mockMvc.perform(get("/api/applications/me").with(as("applicant"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].post.title").value("스모크"));
        mockMvc.perform(get("/api/applications/" + applicationId).with(as("applicant"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/" + postId).with(as("applicant"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myApplicationStatus").value("PENDING"));
        mockMvc.perform(get("/api/users/me").with(as("applicant"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careers[0].organization").value("동호회"));
        mockMvc.perform(get("/api/users/smoke_owner").with(as("applicant"))).andExpect(status().isOk());
    }
}
