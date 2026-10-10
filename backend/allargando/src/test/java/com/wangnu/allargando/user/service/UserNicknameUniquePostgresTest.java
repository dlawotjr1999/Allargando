package com.wangnu.allargando.user.service;

import com.wangnu.allargando.global.exception.ConflictException;
import com.wangnu.allargando.user.dto.UserUpdateRequestDTO;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.CareerRepository;
import com.wangnu.allargando.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * 닉네임 대소문자 무시 UNIQUE(Flyway V7 UK_user_nickname_lower)를 실제 PostgreSQL로 검증한다(USER-T2).
 * H2 테스트는 ddl-auto=create-drop이라 이 함수 인덱스가 만들어지지 않으므로 Flyway가 만든 스키마가 필요하다.
 * 접속 정보·정리 방식은 AuthServiceRegisterPostgresTest와 같다(환경변수 SPRING_DATASOURCE_*, 테스트가 만든 uid만 삭제).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserNicknameUniquePostgresTest {

    private static final String PREFIX = "it-usr-";

    @Autowired PlatformTransactionManager tm;
    @Autowired UserRepository userRepository;
    @Autowired CareerRepository careerRepository;

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(tm).executeWithoutResult(s -> userRepository.findAll().stream()
                .filter(u -> u.getFirebaseUid().startsWith(PREFIX))
                .forEach(userRepository::delete));
    }

    private User saveUser(String key, String nickname) {
        return userRepository.saveAndFlush(User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).firebaseUid(PREFIX + key)
                .phoneNumber(PREFIX + "phone-" + key).nickname(nickname).instrument("바이올린").build());
    }

    // DB 레벨: 대소문자만 다른 닉네임은 저장할 수 없다
    @Test
    void duplicateNicknameDifferingOnlyInCase_isRejectedByDatabase() {
        saveUser("a", "It_Case");

        assertThatThrownBy(() -> saveUser("b", "it_case"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(userRepository.findAll().stream().filter(u -> u.getFirebaseUid().startsWith(PREFIX))).hasSize(1);
    }

    // 조회·중복 확인은 대소문자를 구분하지 않고, 표시는 저장된 대로 돌려준다
    @Test
    void lookupIgnoresCaseAndKeepsStoredDisplay() {
        saveUser("a", "It_Display");

        assertThat(userRepository.existsByNicknameIgnoreCase("it_display")).isTrue();
        assertThat(userRepository.findByNicknameIgnoreCase("IT_DISPLAY")).get()
                .extracting(User::getNickname).isEqualTo("It_Display");
        assertThat(userRepository.existsByNicknameIgnoreCase("it_other")).isFalse();
    }

    // 사전 체크를 통과한 동시 수정이 UNIQUE에 걸리면 일반 409가 아니라 닉네임 409로 알린다
    @Test
    void updateMyInfo_mapsUniqueViolationToNicknameConflict() {
        saveUser("a", "It_Taken");
        User other = saveUser("b", "it_other");

        UserRepository precheckBypassed = mock(UserRepository.class, AdditionalAnswers.delegatesTo(userRepository));
        doReturn(false).when(precheckBypassed).existsByNicknameIgnoreCase(any());
        UserService svc = new UserService(precheckBypassed, careerRepository, mock(ApplicationEventPublisher.class));
        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        when(request.getNickname()).thenReturn("IT_TAKEN");
        when(request.getName()).thenReturn("홍길동");
        when(request.getInstrument()).thenReturn("바이올린");

        // 수동 생성한 서비스는 @Transactional 프록시가 아니므로 바깥 트랜잭션을 직접 열어 준다
        assertThatThrownBy(() -> new TransactionTemplate(tm).execute(s -> svc.updateMyInfo(other, request)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 사용 중인 닉네임입니다");
        assertThat(userRepository.findById(other.getId()).orElseThrow().getNickname()).isEqualTo("it_other");
    }

    // 본인 닉네임의 대소문자만 바꾸는 수정은 UNIQUE에도 걸리지 않고 반영된다
    @Test
    void updateMyInfo_allowsChangingOnlyCaseOfOwnNickname() {
        User me = saveUser("a", "it_mine");
        UserService svc = new UserService(userRepository, careerRepository, mock(ApplicationEventPublisher.class));
        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        when(request.getNickname()).thenReturn("It_Mine");
        when(request.getName()).thenReturn("홍길동");
        when(request.getInstrument()).thenReturn("바이올린");

        new TransactionTemplate(tm).execute(s -> svc.updateMyInfo(me, request));

        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("It_Mine");
    }
}
