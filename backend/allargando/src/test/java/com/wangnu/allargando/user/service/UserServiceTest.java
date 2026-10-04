package com.wangnu.allargando.user.service;

import com.wangnu.allargando.global.exception.BadRequestException;
import com.wangnu.allargando.global.exception.ConflictException;
import com.wangnu.allargando.global.exception.NotFoundException;
import com.wangnu.allargando.user.dto.CareerDTO;
import com.wangnu.allargando.user.dto.UserPublicProfileDTO;
import com.wangnu.allargando.user.dto.UserResponseDTO;
import com.wangnu.allargando.user.dto.UserUpdateRequestDTO;
import com.wangnu.allargando.user.entity.Career;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.event.UserWithdrawalEvent;
import com.wangnu.allargando.user.repository.CareerRepository;
import com.wangnu.allargando.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock CareerRepository careerRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks UserService userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .email("test@test.com")
                .firebaseUid("test-uid")
                .phoneNumber("010-1234-5678")
                .nickname("tester")
                .instrument("바이올린")
                .build();
    }

    @Test
    void getMyInfo_returnsUserWhenExists() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        UserResponseDTO result = userService.getMyInfo(1L);

        assertThat(result.getNickname()).isEqualTo("tester");
        assertThat(result.getEmail()).isEqualTo("test@test.com");
    }

    // 다른 도메인 서비스가 detached User(예: 필터에서 온 @AuthenticationPrincipal)를
    // managed 인스턴스로 재조회할 때 쓰는 진입점 — UserRepository 대신 이 메서드를 거치게 해 서비스 경계를 지킴
    @Test
    void getManagedUserById_returnsManagedUserWhenExists() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        User result = userService.getManagedUserById(1L);

        assertThat(result).isEqualTo(mockUser);
    }

    @Test
    void getManagedUserById_throwsNotFoundWhenMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getManagedUserById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }

    @Test
    void getMyInfo_throwsNotFoundWhenMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyInfo(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }

    @Test
    void getUserProfile_returnsUserWhenExists() {
        given(userRepository.findByNicknameIgnoreCase("tester")).willReturn(Optional.of(mockUser));

        UserPublicProfileDTO result = userService.getUserProfile("tester");

        assertThat(result.getNickname()).isEqualTo("tester");
    }

    @Test
    void getUserProfile_throwsNotFoundWhenMissing() {
        given(userRepository.findByNicknameIgnoreCase("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile("ghost"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }

    @Test
    void updateMyInfo_throwsConflictWhenNicknameDuplicated() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("duplicated");
        given(userRepository.existsByNicknameIgnoreCase("duplicated")).willReturn(true);

        assertThatThrownBy(() -> userService.updateMyInfo(mockUser, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 사용 중인 닉네임입니다");
    }

    @Test
    void updateMyInfo_mapsEachFieldToMatchingProperty() {
        User managedUser = User.builder()
                .id(1L)
                .nickname("tester")
                .instrument("바이올린")
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("tester"); // 닉네임 미변경 → 중복 체크 스킵
        given(request.getInstrument()).willReturn("첼로");

        User inputUser = User.builder().id(1L).build();
        UserResponseDTO result = userService.updateMyInfo(inputUser, request);

        assertThat(result.getInstrument()).isEqualTo("첼로");
    }

    // 경력은 전체 삭제 후 재삽입하고, 내용이 없는 행은 저장하지 않는다
    @Test
    void updateMyInfo_replacesCareersAndSkipsEmptyRows() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        given(request.getInstrument()).willReturn("바이올린");
        given(request.getCareers()).willReturn(List.of(
                CareerDTO.builder().organization("").contexts("").build(),
                CareerDTO.builder().organization("밴드").contexts("").build()));

        userService.updateMyInfo(mockUser, request);

        InOrder inOrder = inOrder(careerRepository);
        inOrder.verify(careerRepository).deleteByUserId(1L);
        org.mockito.ArgumentCaptor<Iterable<Career>> captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        inOrder.verify(careerRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    // careers가 null이면 경력을 건드리지 않는다("경력 미변경")
    @Test
    void updateMyInfo_leavesCareersUntouchedWhenNull() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        given(request.getInstrument()).willReturn("바이올린");
        given(request.getCareers()).willReturn(null); // Mockito 목은 List를 빈 리스트로 돌려주므로 null을 명시한다

        userService.updateMyInfo(mockUser, request);

        verify(careerRepository, never()).deleteByUserId(any());
        verify(careerRepository, never()).saveAll(any());
    }

    // D3: 형식 위반 닉네임은 중복 검사 이전에 400(일반 문구)
    @Test
    void updateMyInfo_throwsBadRequestWhenNicknameInvalid() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("a/b");

        assertThatThrownBy(() -> userService.updateMyInfo(mockUser, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("사용할 수 없는 닉네임입니다");

        verify(userRepository, never()).existsByNicknameIgnoreCase(any());
    }

    // NFD(자모 분리)로 입력된 한글은 NFC로 정규화해 저장한다
    @Test
    void updateMyInfo_savesNicknameNormalizedToNfc() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
        given(userRepository.existsByNicknameIgnoreCase("한글")).willReturn(false);

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn(java.text.Normalizer.normalize("한글", java.text.Normalizer.Form.NFD));
        given(request.getInstrument()).willReturn("바이올린");

        UserResponseDTO result = userService.updateMyInfo(mockUser, request);

        assertThat(result.getNickname()).isEqualTo("한글");
    }

    // 대소문자만 바꾸는 변경은 본인 행이라 중복 검사를 하지 않는다(검사하면 본인 닉네임과 충돌해 409가 된다)
    @Test
    void updateMyInfo_skipsDuplicateCheckWhenOnlyCaseChanges() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("Tester");
        given(request.getInstrument()).willReturn("바이올린");

        UserResponseDTO result = userService.updateMyInfo(mockUser, request);

        assertThat(result.getNickname()).isEqualTo("Tester");
        verify(userRepository, never()).existsByNicknameIgnoreCase(any());
    }

    // 사전 체크를 둘 다 통과한 동시 요청의 UNIQUE 위반은 일반 409가 아니라 닉네임 409로 알린다
    @Test
    void updateMyInfo_throwsNicknameConflictWhenUniqueViolationOnFlush() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
        given(userRepository.existsByNicknameIgnoreCase("newname")).willReturn(false);
        willThrow(new DataIntegrityViolationException("uk_user_nickname_lower")).given(userRepository).flush();

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("newname");
        given(request.getInstrument()).willReturn("바이올린");

        assertThatThrownBy(() -> userService.updateMyInfo(mockUser, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 사용 중인 닉네임입니다");
    }

    @Test
    void checkNickname_throwsBadRequestWhenFormatInvalid() {
        assertThatThrownBy(() -> userService.checkNickname("a b"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("사용할 수 없는 닉네임입니다");

        verify(userRepository, never()).existsByNicknameIgnoreCase(any());
    }

    @Test
    void checkNickname_throwsBadRequestWhenReserved() {
        assertThatThrownBy(() -> userService.checkNickname("Admin"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void checkNickname_returnsTrueWhenDuplicated() {
        given(userRepository.existsByNicknameIgnoreCase("tester")).willReturn(true);

        boolean result = userService.checkNickname("tester");

        assertThat(result).isTrue();
    }

    @Test
    void checkNickname_returnsFalseWhenAvailable() {
        given(userRepository.existsByNicknameIgnoreCase("newname")).willReturn(false);

        boolean result = userService.checkNickname("newname");

        assertThat(result).isFalse();
    }

    // 다른 도메인의 데이터(모집글·지원서·연습일지)가 유저를 FK로 참조하므로, 유저 행을 지우기 전에
    // 탈퇴 이벤트를 먼저 발행해 각 도메인이 자기 데이터를 정리하게 해야 한다
    @Test
    void deleteUser_publishesWithdrawalEventBeforeDeletingUser() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        userService.deleteUser(mockUser);

        InOrder inOrder = inOrder(eventPublisher, userRepository);
        inOrder.verify(eventPublisher).publishEvent(new UserWithdrawalEvent(1L, "test-uid"));
        inOrder.verify(userRepository).delete(mockUser);
    }

    @Test
    void deleteUser_throwsNotFoundWhenMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        User missingUser = User.builder().id(99L).build();

        assertThatThrownBy(() -> userService.deleteUser(missingUser))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
        verifyNoInteractions(eventPublisher);
    }

    // BACKLOG.md #21: application 도메인(지원자 목록)이 여러 유저의 careers를 한 번에 배치 조회할 때 쓰는 진입점
    // — CareerRepository를 직접 주입받지 않고 UserService를 경유하게 해 도메인 경계를 지킴
    @Test
    void getCareersByUserIds_groupsCareersByUserId() {
        User user1 = User.builder().id(1L).build();
        User user2 = User.builder().id(2L).build();
        Career career1 = Career.builder().id(10L).user(user1).organization("서울시향").contexts("연주").build();
        Career career2 = Career.builder().id(11L).user(user2).organization("경기필하모닉").contexts("지도").build();
        given(careerRepository.findByUserIdIn(List.of(1L, 2L))).willReturn(List.of(career1, career2));

        Map<Long, List<CareerDTO>> result = userService.getCareersByUserIds(List.of(1L, 2L));

        assertThat(result.get(1L)).extracting(CareerDTO::getOrganization).containsExactly("서울시향");
        assertThat(result.get(2L)).extracting(CareerDTO::getOrganization).containsExactly("경기필하모닉");
    }

    @Test
    void getCareersByUserIds_returnsEmptyMapWhenNoneFound() {
        given(careerRepository.findByUserIdIn(List.of(99L))).willReturn(List.of());

        Map<Long, List<CareerDTO>> result = userService.getCareersByUserIds(List.of(99L));

        assertThat(result).isEmpty();
    }

    // 신고·차단처럼 닉네임만 아는 쪽이 대상 유저를 찾을 때 쓰는 진입점
    @Test
    void getManagedUserByNickname_returnsUserWhenExists() {
        given(userRepository.findByNicknameIgnoreCase("tester")).willReturn(Optional.of(mockUser));

        assertThat(userService.getManagedUserByNickname("tester")).isEqualTo(mockUser);
    }

    @Test
    void getManagedUserByNickname_throwsNotFoundWhenMissing() {
        given(userRepository.findByNicknameIgnoreCase("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getManagedUserByNickname("ghost"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }
}
