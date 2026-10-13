package com.wangnu.allargando.application.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.wangnu.allargando.application.entity.Application;
import com.wangnu.allargando.application.entity.ApplicationStatus;
import com.wangnu.allargando.application.repository.ApplicationRepository;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.post.repository.PostRepository;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 같은 악기에 대한 지원을 모집자가 동시에 수락할 때 정원 불변식이 지켜지는지 실제 PostgreSQL로 검증한다(§3.6).
 * Mockito·H2 단일 스레드 테스트로는 PostInstrument @Version 충돌과 DB CHECK가 실제로 작동하는지 볼 수 없다.
 *
 * - 검증하는 것은 "어떤 인터리빙이든 항상 참인 불변식"이다: 확정 인원 == 수락된 지원 수 ≤ 정원, 마감 상태 일치.
 *   누가 이겼는지·충돌이 났는지는 단언하지 않는다(타이밍 의존 → 플레이키). @Version이 없으면 수락들이 같은
 *   confirmed를 읽고 덮어써 정원보다 많은 수락이 생긴다 — PostInstrument의 @Version을 임시로 빼고 돌려
 *   여러 석 테스트가 실패하는 것을 확인했다(한 석 테스트는 겹침 타이밍에 따라 통과할 수 있다).
 * - 모든 스레드가 시작 래치를 함께 넘어 트랜잭션이 최대한 겹치게 하고, 라운드를 반복해 겹침 확률을 높인다.
 * - 접속 정보·정리 방식은 다른 *PostgresTest와 같다(테스트가 만든 uid만 삭제).
 */
@SpringBootTest
class ApplicationAcceptConcurrencyPostgresTest {

    private static final String PREFIX = "it-accept-";
    private static final int ROUNDS = 10;

    @Autowired ApplicationService applicationService;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean FirebaseMessaging firebaseMessaging; // 알림 발송은 시험 대상이 아니다

    @BeforeEach
    @AfterEach
    void cleanUp() {
        String ours = "(select user_id from \"user\" where firebase_uid like '" + PREFIX + "%')";
        jdbcTemplate.update("delete from application where user_id in " + ours);
        jdbcTemplate.update("delete from post_instrument where post_id in (select post_id from post where user_id in " + ours + ")");
        jdbcTemplate.update("delete from post where user_id in " + ours);
        jdbcTemplate.update("delete from \"user\" where firebase_uid like '" + PREFIX + "%'");
    }

    private User saveUser(String key) {
        return userRepository.save(User.builder().name("테스터").termsAgreedAt(LocalDateTime.now())
                .firebaseUid(PREFIX + key).phoneNumber(PREFIX + "p-" + key)
                .nickname("acc_" + key).instrument("바이올린").build());
    }

    // 정원 people인 바이올린 1석 글과, 서로 다른 지원자 applicants명의 PENDING 지원을 만든다
    private Seed seed(String key, int people, int applicants) {
        User owner = saveUser(key + "-owner");
        Post post = Post.create(owner, PostInfo.builder().category("앙상블").title("동시 수락")
                .eventAt(LocalDateTime.now().plusDays(7)).location("서울").region("서울").timetable("13:00").build());
        post.addInstrument(PostInstrument.of(post, "바이올린", people));
        Long postId = postRepository.save(post).getId();
        List<Long> applicationIds = new ArrayList<>();
        for (int i = 0; i < applicants; i++) {
            User applicant = saveUser(key + "-a" + i);
            applicationIds.add(applicationRepository.save(Application.builder().user(applicant).post(post)
                    .instrument("바이올린").status(ApplicationStatus.PENDING).build()).getId());
        }
        return new Seed(owner, postId, applicationIds);
    }

    // 각 지원을 서로 다른 스레드에서 동시에 수락한다. 성공한 건수를 돌려주고 예외는 삼킨다(충돌·마감 400은 정상 경로)
    private int acceptAllConcurrently(Seed seed) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(seed.applicationIds().size());
        CountDownLatch ready = new CountDownLatch(seed.applicationIds().size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (Long applicationId : seed.applicationIds()) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        applicationService.accept(seed.owner(), applicationId);
                        return true;
                    } catch (RuntimeException expected) {
                        return false;
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            int succeeded = 0;
            for (Future<Boolean> f : results) {
                if (f.get(30, TimeUnit.SECONDS)) succeeded++;
            }
            return succeeded;
        } finally {
            pool.shutdownNow();
        }
    }

    // 불변식: 확정 인원 == 수락된 지원 수, 확정 ≤ 정원, 마감 여부 == (확정 ≥ 정원)
    private void assertInvariant(Seed seed, int people, int succeeded) {
        Map<String, Object> pi = jdbcTemplate.queryForMap(
                "select confirmed, closed from post_instrument where post_id = ?", seed.postId());
        int confirmed = ((Number) pi.get("confirmed")).intValue();
        boolean closed = (Boolean) pi.get("closed");
        Integer accepted = jdbcTemplate.queryForObject(
                "select count(*) from application where post_id = ? and status = 'ACCEPTED'",
                Integer.class, seed.postId());

        assertThat(accepted).as("수락 상태 지원 수").isEqualTo(succeeded);
        assertThat(confirmed).as("확정 인원은 수락된 지원 수와 같아야 한다(lost update 없음)").isEqualTo(accepted);
        assertThat(confirmed).as("확정 인원은 정원을 넘지 않는다").isLessThanOrEqualTo(people);
        assertThat(closed).as("마감 여부는 확정 ≥ 정원").isEqualTo(confirmed >= people);
    }

    @Test
    void accept_neverExceedsCapacityWhenManyApplicantsRaceForOneSeat() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            cleanUp();
            Seed seed = seed("one-" + round, 1, 6);

            int succeeded = acceptAllConcurrently(seed);

            assertThat(succeeded).as("정원 1석에 수락은 최대 1건").isLessThanOrEqualTo(1);
            assertInvariant(seed, 1, succeeded);
        }
    }

    @Test
    void accept_keepsConfirmedEqualToAcceptedWhenApplicantsRaceForSeveralSeats() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            cleanUp();
            Seed seed = seed("many-" + round, 3, 6);

            int succeeded = acceptAllConcurrently(seed);

            assertThat(succeeded).as("정원 3석에 수락은 최대 3건").isLessThanOrEqualTo(3);
            assertInvariant(seed, 3, succeeded);
        }
    }

    private record Seed(User owner, Long postId, List<Long> applicationIds) {}
}
