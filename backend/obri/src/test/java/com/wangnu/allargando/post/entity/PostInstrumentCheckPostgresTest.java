package com.wangnu.allargando.post.entity;

import com.wangnu.allargando.global.common.APIResponse;
import com.wangnu.allargando.global.exception.GlobalExceptionHandler;
import com.wangnu.allargando.post.repository.PostRepository;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/*
 * post_instrument 정원 불변식 CHECK(Flyway V8)을 실제 PostgreSQL로 검증한다(POST-T5).
 * H2 테스트는 ddl-auto=create-drop이라 V8의 CHECK가 만들어지지 않으므로 Flyway가 만든 스키마가 필요하다.
 * 거부 테스트는 "어떤 제약이 걸렸는지"까지 단언한다 — 다른 NOT NULL·FK 위반으로 우연히 통과하는 것을 막기 위함이다.
 * 접속 정보·정리 방식은 다른 *PostgresTest와 같다(환경변수 SPRING_DATASOURCE_*, 테스트가 만든 uid만 삭제).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PostInstrumentCheckPostgresTest {

    private static final String PREFIX = "it-pic-";

    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    // 지연 로딩을 건드리지 않도록 JDBC로 지운다(FK 순서: 악기 → 글 → 유저). 이전 실행이 남긴 행도 시작 전에 정리한다
    @BeforeEach
    @AfterEach
    void cleanUp() {
        String ourUsers = "(select user_id from \"user\" where firebase_uid like '" + PREFIX + "%')";
        String ourPosts = "(select post_id from post where user_id in " + ourUsers + ")";
        jdbcTemplate.update("delete from post_instrument where post_id in " + ourPosts);
        jdbcTemplate.update("delete from post where user_id in " + ourUsers);
        jdbcTemplate.update("delete from \"user\" where firebase_uid like '" + PREFIX + "%'");
    }

    private Post newPost() {
        User owner = userRepository.saveAndFlush(User.builder().firebaseUid(PREFIX + "owner")
                .phoneNumber(PREFIX + "phone").nickname("it_pic_owner").instrument("바이올린").build());
        return Post.create(owner, PostInfo.builder().category("앙상블").title("t")
                .eventAt(LocalDateTime.now().plusDays(5)).location("l").region("서울").timetable("t").build());
    }

    // 글에 악기를 붙인다. confirmed는 엔티티 규칙(Post/PostInstrument)을 우회해 직접 지정한다 — DB 제약만 시험하려는 것이다
    private void addInstrument(Post post, String name, int people, int confirmed) {
        PostInstrument instrument = PostInstrument.of(post, name, people);
        ReflectionTestUtils.setField(instrument, "confirmed", confirmed);
        ReflectionTestUtils.setField(instrument, "closed", confirmed >= people);
        post.addInstrument(instrument);
    }

    // 저장이 CHECK 위반으로 거부되는지 확인하고, 걸린 제약명을 돌려준다
    private String violatedConstraintOf(Post post) {
        DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> postRepository.saveAndFlush(post));
        assertThat(e).as("제약 위반이 일어나야 한다").isNotNull();
        assertThat(handle(e).getStatusCode()).as("입력 문제로 분류돼 400").isEqualTo(HttpStatus.BAD_REQUEST);
        Throwable cause = e.getCause();
        assertThat(cause).isInstanceOf(ConstraintViolationException.class);
        return ((ConstraintViolationException) cause).getConstraintName();
    }

    private ResponseEntity<APIResponse<Void>> handle(DataIntegrityViolationException e) {
        return new GlobalExceptionHandler().handleDataIntegrityViolationException(e);
    }

    // 정상 값(확정이 정원과 같은 마감 상태 포함)은 저장된다
    @Test
    void validInstrumentRowsAreAccepted() {
        Post post = newPost();
        addInstrument(post, "바이올린", 2, 2);
        addInstrument(post, "첼로", 1, 0);

        Post saved = postRepository.saveAndFlush(post);

        assertThat(saved.getId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from post_instrument where post_id = ?", Integer.class, saved.getId())).isEqualTo(2);
    }

    // 모집 인원 0 이하는 CHECK로 거부된다
    @Test
    void zeroPeopleIsRejectedByPositivePeopleCheck() {
        Post post = newPost();
        addInstrument(post, "바이올린", 0, 0);

        assertThat(violatedConstraintOf(post)).isEqualTo("ck_post_instrument_people_positive");
    }

    @Test
    void negativeConfirmedIsRejectedByNonNegativeCheck() {
        Post post = newPost();
        addInstrument(post, "바이올린", 2, -1);

        assertThat(violatedConstraintOf(post)).isEqualTo("ck_post_instrument_confirmed_non_negative");
    }

    // 확정 인원이 모집 인원을 넘는 상태(과거 정원 축소 버그가 만들던 상태)를 DB가 거부한다
    @Test
    void confirmedGreaterThanPeopleIsRejectedByWithinPeopleCheck() {
        Post post = newPost();
        addInstrument(post, "바이올린", 1, 2);

        assertThat(violatedConstraintOf(post)).isEqualTo("ck_post_instrument_confirmed_within_people");
    }

    // 이미 저장된 행을 UPDATE로 위반시키는 경로(정원 축소)도 막는다 — 앱 검증을 우회한 직접 수정의 마지막 방어선
    @Test
    void shrinkingPeopleBelowConfirmedByDirectUpdateIsRejected() {
        Post post = newPost();
        addInstrument(post, "바이올린", 2, 2);
        Post saved = postRepository.saveAndFlush(post);

        DataIntegrityViolationException e = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("update post_instrument set people = 1 where post_id = ?", saved.getId()));

        assertThat(e).isNotNull();
        assertThat(e.getMostSpecificCause().getMessage()).contains("ck_post_instrument_confirmed_within_people");
    }

    // V8이 만든 제약 이름 3개가 스키마에 존재한다
    @Test
    void checkConstraintsExistInSchema() {
        List<String> names = jdbcTemplate.queryForList(
                "select conname from pg_constraint where contype = 'c' and conrelid = 'post_instrument'::regclass",
                String.class);

        assertThat(names).contains("ck_post_instrument_people_positive",
                "ck_post_instrument_confirmed_non_negative", "ck_post_instrument_confirmed_within_people");
    }
}
