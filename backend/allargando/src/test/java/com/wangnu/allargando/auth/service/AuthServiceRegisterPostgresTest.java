package com.wangnu.allargando.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.wangnu.allargando.auth.dto.RegisterRequestDTO;
import com.wangnu.allargando.auth.dto.RegisterResponseDTO;
import com.wangnu.allargando.global.exception.ConflictException;
import com.wangnu.allargando.user.dto.CareerDTO;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.CareerRepository;
import com.wangnu.allargando.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * AuthService.register의 멱등·동시성·원자성을 실제 PostgreSQL로 검증한다(AUTH-T2·T3).
 * H2·Mockito로는 UNIQUE 경쟁 시 락 대기와 위반 시점, 트랜잭션 롤백을 재현할 수 없어 별도로 둔다.
 *
 * - DB 접속 정보는 application.properties(환경변수 SPRING_DATASOURCE_*)를 그대로 쓴다. CI는 service container,
 *   로컬은 `docker compose up`한 PostgreSQL을 가리키면 되고, Flyway가 만든 실제 스키마를 validate로 검증한다.
 * - 실제 커밋이 있어야 다른 스레드가 보므로 테스트 트랜잭션을 끈다(NOT_SUPPORTED). 대신 이 테스트가 만든
 *   firebase_uid(PREFIX)의 행만 지워 개발 DB의 다른 데이터는 건드리지 않는다.
 * - 경쟁은 "사전 체크를 둘 다 통과한 뒤 저장"을 배리어로 강제해 결정적으로 만든다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceRegisterPostgresTest {

    private static final String PREFIX = "it-reg-";

    @Autowired PlatformTransactionManager tm;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;

    private final FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);

    @AfterEach
    void cleanUp() {
        TransactionTemplate tt = new TransactionTemplate(tm);
        tt.executeWithoutResult(s -> userRepository.findAll().stream()
                .filter(u -> u.getFirebaseUid().startsWith(PREFIX))
                .forEach(userRepository::delete)); // careers는 cascade로 함께 삭제
    }

    // 토큰 문자열마다 (uid, email, phone) 클레임을 가진 Firebase 토큰을 돌려주도록 Mock을 구성
    private void givenToken(String token, String uid, String email, String phone) throws Exception {
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getUid()).thenReturn(PREFIX + uid);
        when(decoded.getEmail()).thenReturn(email);
        when(decoded.getClaims()).thenReturn(Map.of("phone_number", phone));
        when(firebaseAuth.verifyIdToken(token, true)).thenReturn(decoded);
    }

    private RegisterRequestDTO request(String nickname, List<CareerDTO> careers) {
        RegisterRequestDTO r = mock(RegisterRequestDTO.class);
        when(r.getNickname()).thenReturn(nickname);
        when(r.getName()).thenReturn("홍길동");
        when(r.getInstrument()).thenReturn("바이올린");
        when(r.getCareers()).thenReturn(careers);
        return r;
    }

    private AuthService service(UserRepository users, CareerRepository careers) {
        return new AuthService(firebaseAuth, users, careers, new TransactionTemplate(tm));
    }

    // existsByNicknameIgnoreCase(사전 체크의 마지막 단계)에서 두 스레드를 만나게 해 둘 다 사전 체크를 통과한 뒤 저장하도록 강제.
    // 첫 두 번의 호출(스레드당 한 번)에서만 기다린다 — 패자가 UNIQUE 위반 뒤 재검사로 다시 호출할 때 혼자 대기하지 않게 한다
    private UserRepository repositoryRacingAfterPreChecks(CyclicBarrier barrier) {
        UserRepository racing = mock(UserRepository.class, AdditionalAnswers.delegatesTo(userRepository));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            boolean exists = userRepository.existsByNicknameIgnoreCase(inv.getArgument(0));
            if (calls.incrementAndGet() <= 2) {
                barrier.await(10, TimeUnit.SECONDS);
            }
            return exists;
        }).when(racing).existsByNicknameIgnoreCase(any());
        return racing;
    }

    // 두 호출을 동시에 실행해 각각 (결과 또는 예외)를 돌려준다
    private List<Object> runConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : List.of(first, second)) {
            futures.add(pool.submit(() -> {
                try {
                    return task.call();
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private long countUsersByUid(String uid) {
        return userRepository.findAll().stream().filter(u -> u.getFirebaseUid().equals(PREFIX + uid)).count();
    }

    // 같은 uid 동시 가입(더블탭): 둘 다 성공하고, 같은 가입 시각을 받고, Firebase 계정은 지워지지 않고, 행은 1개
    @Test
    void register_concurrentSameUid_bothSucceedWithoutDeletingFirebaseAccount() throws Exception {
        givenToken("tok", "same", "same@t.com", "+821000000001");
        RegisterRequestDTO req = request("it_same", null);
        AuthService svc = service(repositoryRacingAfterPreChecks(new CyclicBarrier(2)), careerRepository);

        List<Object> results = runConcurrently(() -> svc.register("tok", req), () -> svc.register("tok", req));

        assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(RegisterResponseDTO.class));
        assertThat(((RegisterResponseDTO) results.get(0)).getCreatedAt())
                .isEqualTo(((RegisterResponseDTO) results.get(1)).getCreatedAt());
        assertThat(countUsersByUid("same")).isEqualTo(1);
        verify(firebaseAuth, never()).deleteUser(anyString());
    }

    // 서로 다른 uid가 같은 전화번호로 경쟁: 한쪽만 가입되고 패자는 구체적인 409, 어느 쪽 Firebase 계정도 지우지 않는다
    @Test
    void register_concurrentSamePhoneNumber_loserGetsConflict() throws Exception {
        givenToken("tokA", "a", "a@t.com", "+821000000002");
        givenToken("tokB", "b", "b@t.com", "+821000000002");
        RegisterRequestDTO reqA = request("it_phone_a", null);
        RegisterRequestDTO reqB = request("it_phone_b", null);
        AuthService svc = service(repositoryRacingAfterPreChecks(new CyclicBarrier(2)), careerRepository);

        List<Object> results = runConcurrently(() -> svc.register("tokA", reqA), () -> svc.register("tokB", reqB));

        assertThat(results).filteredOn(r -> r instanceof RegisterResponseDTO).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof ConflictException)
                .singleElement()
                .satisfies(e -> assertThat(((Throwable) e).getMessage()).isEqualTo("이미 가입된 전화번호입니다"));
        assertThat(countUsersByUid("a") + countUsersByUid("b")).isEqualTo(1);
        verify(firebaseAuth, never()).deleteUser(anyString());
    }

    // 대소문자만 다른 닉네임으로 경쟁(V7 lower(nickname) UNIQUE): 한쪽만 가입되고 패자는 닉네임 409, Firebase 계정은 지우지 않는다
    @Test
    void register_concurrentSameNicknameDifferentCase_loserGetsConflict() throws Exception {
        givenToken("tokA", "na", "na@t.com", "+821000000005");
        givenToken("tokB", "nb", "nb@t.com", "+821000000006");
        RegisterRequestDTO reqA = request("It_Nick", null);
        RegisterRequestDTO reqB = request("it_nick", null);
        AuthService svc = service(repositoryRacingAfterPreChecks(new CyclicBarrier(2)), careerRepository);

        List<Object> results = runConcurrently(() -> svc.register("tokA", reqA), () -> svc.register("tokB", reqB));

        assertThat(results).filteredOn(r -> r instanceof RegisterResponseDTO).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof ConflictException)
                .singleElement()
                .satisfies(e -> assertThat(((Throwable) e).getMessage()).isEqualTo("이미 사용 중인 닉네임입니다"));
        assertThat(countUsersByUid("na") + countUsersByUid("nb")).isEqualTo(1);
        verify(firebaseAuth, never()).deleteUser(anyString());
    }

    // 같은 uid를 본문만 바꿔 다시 호출하면 첫 가입 결과가 그대로 나오고 프로필은 바뀌지 않는다
    @Test
    void register_calledAgainWithSameUid_returnsFirstResultAndIgnoresBody() throws Exception {
        givenToken("tok", "again", "again@t.com", "+821000000003");
        AuthService svc = service(userRepository, careerRepository);

        RegisterResponseDTO first = svc.register("tok", request("it_first", null));
        RegisterResponseDTO second = svc.register("tok", request("it_second", null));

        assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
        User saved = userRepository.findByFirebaseUid(PREFIX + "again").orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("it_first");
        assertThat(countUsersByUid("again")).isEqualTo(1);
    }

    // 경력 저장이 실패하면 유저 행도 함께 롤백된다(반쪽 가입 없음). 같은 토큰으로 재시도하면 경력까지 저장된다
    @Test
    void register_careerSaveFails_rollsBackUserAndRetryWithSameTokenSucceeds() throws Exception {
        givenToken("tok", "career", "career@t.com", "+821000000004");
        List<CareerDTO> careers = List.of(CareerDTO.builder().organization("오케스트라").contexts("바이올린").build());
        RegisterRequestDTO req = request("it_career", careers);

        AtomicBoolean failOnce = new AtomicBoolean(true);
        CareerRepository flaky = mock(CareerRepository.class, AdditionalAnswers.delegatesTo(careerRepository));
        doAnswer(inv -> {
            if (failOnce.getAndSet(false)) {
                throw new DataIntegrityViolationException("career insert failed");
            }
            return careerRepository.saveAll(inv.<Iterable<com.wangnu.allargando.user.entity.Career>>getArgument(0));
        }).when(flaky).saveAll(any());
        AuthService svc = service(userRepository, flaky);

        assertThatThrownBy(() -> svc.register("tok", req)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countUsersByUid("career")).isZero();

        svc.register("tok", req);

        User saved = userRepository.findByFirebaseUid(PREFIX + "career").orElseThrow();
        assertThat(careerRepository.findByUserIdIn(List.of(saved.getId()))).hasSize(1);
        verify(firebaseAuth, never()).deleteUser(anyString());
    }
}
