package com.obri_back.obri.user.service;

import com.obri_back.obri.global.exception.ConflictException;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.user.dto.CareerDTO;
import com.obri_back.obri.user.dto.UserPublicProfileDTO;
import com.obri_back.obri.user.dto.UserResponseDTO;
import com.obri_back.obri.user.dto.UserUpdateRequestDTO;
import com.obri_back.obri.user.entity.Career;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.obri_back.obri.user.repository.CareerRepository;
import com.obri_back.obri.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
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
        given(userRepository.findByNickname("tester")).willReturn(Optional.of(mockUser));

        UserPublicProfileDTO result = userService.getUserProfile("tester");

        assertThat(result.getNickname()).isEqualTo("tester");
    }

    @Test
    void getUserProfile_throwsNotFoundWhenMissing() {
        given(userRepository.findByNickname("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile("ghost"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }

    @Test
    void updateMyInfo_throwsConflictWhenNicknameDuplicated() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        UserUpdateRequestDTO request = mock(UserUpdateRequestDTO.class);
        given(request.getNickname()).willReturn("duplicated");
        given(userRepository.existsByNickname("duplicated")).willReturn(true);

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

    @Test
    void checkNickname_returnsTrueWhenDuplicated() {
        given(userRepository.existsByNickname("tester")).willReturn(true);

        boolean result = userService.checkNickname("tester");

        assertThat(result).isTrue();
    }

    @Test
    void checkNickname_returnsFalseWhenAvailable() {
        given(userRepository.existsByNickname("newname")).willReturn(false);

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
}