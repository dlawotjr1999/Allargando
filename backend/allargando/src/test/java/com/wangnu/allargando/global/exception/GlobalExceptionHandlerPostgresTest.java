package com.wangnu.allargando.global.exception;

import com.wangnu.allargando.block.entity.UserBlock;
import com.wangnu.allargando.block.repository.UserBlockRepository;
import com.wangnu.allargando.global.common.APIResponse;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/*
 * DB 제약 위반 분류(GlobalExceptionHandler)를 실제 PostgreSQL이 만드는 예외로 검증한다(GLB-T6).
 * 단위 테스트의 가짜 예외는 Hibernate·Spring이 실제로 감싸는 구조(SQLSTATE·제약명 위치)를 보장하지 못하고,
 * H2는 제약명·SQLSTATE가 다를 수 있어 PostgreSQL(Flyway 스키마)이 필요하다.
 * 접속 정보·정리 방식은 다른 *PostgresTest와 같다(환경변수 SPRING_DATASOURCE_*, 테스트가 만든 uid만 삭제).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GlobalExceptionHandlerPostgresTest {

    private static final String PREFIX = "it-geh-";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Autowired UserRepository userRepository;
    @Autowired UserBlockRepository userBlockRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    // 지연 로딩 프록시를 건드리지 않도록 JDBC로 지운다(FK 순서: 차단 → 유저). 이전 실행이 남긴 행도 시작 전에 정리한다
    @BeforeEach
    @AfterEach
    void cleanUp() {
        String ours = "(select user_id from \"user\" where firebase_uid like '" + PREFIX + "%')";
        jdbcTemplate.update("delete from user_block where blocker_id in " + ours + " or blocked_id in " + ours);
        jdbcTemplate.update("delete from \"user\" where firebase_uid like '" + PREFIX + "%'");
    }

    private User saveUser(String key) {
        return userRepository.saveAndFlush(User.builder().firebaseUid(PREFIX + key)
                .phoneNumber(PREFIX + "phone-" + key).nickname("it_geh_" + key).instrument("바이올린").build());
    }

    // 실제 DB가 던지는 예외를 받아 핸들러에 넘긴다
    private ResponseEntity<APIResponse<Void>> handle(Supplier<?> violatingStatement) {
        DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
                violatingStatement::get);
        assertThat(e).as("제약 위반이 일어나야 한다").isNotNull();
        return handler.handleDataIntegrityViolationException(e);
    }

    // 알려진 UNIQUE 제약(차단 쌍) → 서비스 사전 체크와 같은 문구의 409
    @Test
    void duplicateBlockPair_returns409WithSpecificMessage() {
        User a = saveUser("a");
        User b = saveUser("b");
        userBlockRepository.saveAndFlush(UserBlock.of(a, b));

        ResponseEntity<APIResponse<Void>> response = handle(() -> userBlockRepository.saveAndFlush(UserBlock.of(a, b)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("이미 차단한 사용자입니다");
    }

    // 문구 매핑이 없는 UNIQUE(전화번호) → 409 + 일반 중복 문구
    @Test
    void duplicatePhoneNumber_returns409WithGenericDuplicateMessage() {
        saveUser("a");

        ResponseEntity<APIResponse<Void>> response = handle(() -> userRepository.saveAndFlush(User.builder()
                .firebaseUid(PREFIX + "other").phoneNumber(PREFIX + "phone-a").nickname("it_geh_other")
                .instrument("바이올린").build()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("이미 존재하는 데이터입니다");
    }

    // V7의 닉네임 함수 인덱스 위반도 UNIQUE로 분류된다(대소문자만 다른 닉네임)
    @Test
    void duplicateNicknameDifferingInCase_returns409() {
        saveUser("a");

        ResponseEntity<APIResponse<Void>> response = handle(() -> userRepository.saveAndFlush(User.builder()
                .firebaseUid(PREFIX + "other").phoneNumber(PREFIX + "phone-other").nickname("IT_GEH_A")
                .instrument("바이올린").build()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // FK 위반(다른 행이 참조 중인 유저 삭제) → 409 + "연관된 데이터" 문구
    @Test
    void deletingReferencedUser_returns409WithForeignKeyMessage() {
        User a = saveUser("a");
        User b = saveUser("b");
        userBlockRepository.saveAndFlush(UserBlock.of(a, b));

        ResponseEntity<APIResponse<Void>> response = handle(() -> {
            userRepository.delete(b);
            userRepository.flush();
            return null;
        });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("연관된 데이터가 있어 처리할 수 없습니다");
    }

    // NOT NULL 위반 → 400 (입력 누락)
    @Test
    void nullRequiredColumn_returns400() {
        ResponseEntity<APIResponse<Void>> response = handle(() -> userRepository.saveAndFlush(User.builder()
                .firebaseUid(PREFIX + "a").phoneNumber(PREFIX + "phone-a").nickname(null)
                .instrument("바이올린").build()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("입력값이 올바르지 않습니다");
    }

    // 컬럼 길이(255) 초과 → 409가 아니라 400 (경력 256자 입력이 일반 409로 나가던 문제의 근본 해소)
    @Test
    void valueLongerThanColumn_returns400() {
        ResponseEntity<APIResponse<Void>> response = handle(() -> userRepository.saveAndFlush(User.builder()
                .firebaseUid(PREFIX + "a").phoneNumber(PREFIX + "phone-a").nickname("n".repeat(256))
                .instrument("바이올린").build()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // 핸들러가 문구를 매핑한 제약명이 실제 스키마에 존재하는지 확인한다 — V1 baseline의 application 제약명처럼
    // Hibernate가 생성한 이름을 문자열로 들고 있으므로, 스키마가 바뀌어 어긋나면 여기서 잡는다
    @Test
    void mappedUniqueConstraintNames_existInSchema() {
        List<String> names = jdbcTemplate.queryForList(
                "select conname from pg_constraint where contype = 'u' "
                        + "and conrelid::regclass::text in ('application', 'report', 'user_block')", String.class);

        assertThat(names).contains("uk1m278y6v8jh3b4i9aeetf3dc2", "uk_report_reporter_target", "uk_user_block_pair");
    }
}
