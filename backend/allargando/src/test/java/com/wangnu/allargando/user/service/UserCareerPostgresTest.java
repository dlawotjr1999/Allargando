package com.wangnu.allargando.user.service;

import com.wangnu.allargando.user.dto.CareerDTO;
import com.wangnu.allargando.user.dto.UserResponseDTO;
import com.wangnu.allargando.user.dto.UserUpdateRequestDTO;
import com.wangnu.allargando.user.entity.Career;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.CareerRepository;
import com.wangnu.allargando.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * 경력 교체(전체 삭제 후 재삽입)를 실제 PostgreSQL·JPA로 검증한다(USER-T5·T10).
 * Mockito 단위 테스트는 삭제 → 삽입 → 응답 로딩의 실제 순서를 확인하지 못한다(USER-8: 응답이 옛 경력을 보일 위험).
 * 접속 정보·정리 방식은 다른 *PostgresTest와 같다(환경변수 SPRING_DATASOURCE_*, 테스트가 만든 uid만 삭제).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserCareerPostgresTest {

    private static final String PREFIX = "it-career-";

    @Autowired PlatformTransactionManager tm;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(tm).executeWithoutResult(s -> userRepository.findAll().stream()
                .filter(u -> u.getFirebaseUid().startsWith(PREFIX))
                .forEach(userRepository::delete)); // careers는 cascade로 함께 삭제
    }

    private User saveUser() {
        return userRepository.saveAndFlush(User.builder().firebaseUid(PREFIX + "a").phoneNumber(PREFIX + "phone")
                .nickname("it_career").instrument("바이올린").build());
    }

    private UserService service() {
        return new UserService(userRepository, careerRepository, mock(ApplicationEventPublisher.class));
    }

    private UserResponseDTO updateCareers(User me, List<CareerDTO> careers) {
        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        when(request.getNickname()).thenReturn(me.getNickname());
        when(request.getInstrument()).thenReturn(me.getInstrument());
        when(request.getCareers()).thenReturn(careers);
        // 수동 생성한 서비스는 @Transactional 프록시가 아니므로 바깥 트랜잭션을 직접 열어 준다
        return new TransactionTemplate(tm).execute(s -> service().updateMyInfo(me, request));
    }

    private CareerDTO dto(String organization, String contexts) {
        return CareerDTO.builder().organization(organization).contexts(contexts).build();
    }

    private List<Career> savedCareers(User me) {
        return careerRepository.findByUserIdIn(List.of(me.getId()));
    }

    // 기존 경력은 모두 지워지고 요청 목록으로 대체되며, 응답에는 교체 후의 경력이 나온다
    @Test
    void updateMyInfo_replacesAllCareersAndResponseShowsNewOnes() {
        User me = saveUser();
        updateCareers(me, List.of(dto("옛 오케스트라", "바이올린"), dto("옛 밴드", "베이스")));
        assertThat(savedCareers(me)).hasSize(2);

        UserResponseDTO result = updateCareers(me, List.of(dto("새 동아리", "첼로")));

        assertThat(savedCareers(me)).singleElement().extracting(Career::getOrganization).isEqualTo("새 동아리");
        assertThat(result.getCareers()).singleElement().extracting(CareerDTO::getOrganization).isEqualTo("새 동아리");
    }

    // 단체명·설명이 비어도 저장되고(혼자 연주한 경우), 아무 내용 없는 행은 저장되지 않는다. 비어 있는 쪽은 빈 문자열로 남는다
    @Test
    void updateMyInfo_savesBlankFieldsAsEmptyStringAndDropsEmptyRows() {
        User me = saveUser();

        updateCareers(me, List.of(dto("", "혼자 5년 연주"), dto("동아리", null), dto("", "")));

        assertThat(savedCareers(me)).hasSize(2)
                .extracting(Career::getOrganization, Career::getContexts)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("", "혼자 5년 연주"),
                        org.assertj.core.groups.Tuple.tuple("동아리", ""));
    }

    // 빈 목록을 보내면 경력이 모두 지워지고, null이면 그대로 둔다
    @Test
    void updateMyInfo_emptyListClearsCareersAndNullLeavesThem() {
        User me = saveUser();
        updateCareers(me, List.of(dto("밴드", "베이스")));

        updateCareers(me, null);
        assertThat(savedCareers(me)).hasSize(1);

        updateCareers(me, List.of());
        assertThat(savedCareers(me)).isEmpty();
    }

    // 컬럼 길이(255) 경계: 255자는 저장되고, 256자는 DB가 거절한다(그래서 요청 단계에서 400으로 먼저 막아야 한다)
    @Test
    void updateMyInfo_acceptsExactly255CharsAndDatabaseRejects256() {
        User me = saveUser();

        updateCareers(me, List.of(dto("a".repeat(255), "b".repeat(255))));
        assertThat(savedCareers(me)).singleElement().extracting(c -> c.getOrganization().length()).isEqualTo(255);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> updateCareers(me, List.of(dto("a".repeat(256), "c"))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(savedCareers(me)).singleElement().extracting(c -> c.getOrganization().length()).isEqualTo(255);
    }
}
