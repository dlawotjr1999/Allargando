package com.obri_back.obri.auth.service;

import com.google.firebase.auth.AuthErrorCode;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.obri_back.obri.auth.dto.FCMTokenUpdateRequestDTO;
import com.obri_back.obri.auth.dto.RegisterRequestDTO;
import com.obri_back.obri.global.exception.ConflictException;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.global.exception.RegistrationFailedException;
import com.obri_back.obri.global.exception.UnauthorizedException;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.repository.CareerRepository;
import com.obri_back.obri.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock FirebaseAuth firebaseAuth;
    @Mock UserRepository userRepository;
    @Mock CareerRepository careerRepository;

    @InjectMocks AuthService authService;

    private FirebaseToken mockToken;

    @BeforeEach
    void setUp() throws Exception {
        mockToken = mock(FirebaseToken.class);
        lenient().when(mockToken.getUid()).thenReturn("test-uid");
        lenient().when(mockToken.getEmail()).thenReturn("test@test.com");
        lenient().when(mockToken.getClaims())
                .thenReturn(Map.of("phone_number", "010-1234-5678"));
    }

    @Test
    void register_savesUserWhenValid() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false);
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        given(request.getCareers()).willReturn(null);

        authService.register("valid-token", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo("010-1234-5678");
    }

    @Test
    void register_rollsBackFirebaseWhenSaveFails() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false);
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(RegistrationFailedException.class)
                .hasMessage("회원가입 중 오류가 발생했습니다");

        verify(firebaseAuth, times(1)).deleteUser("test-uid");
    }

    @Test
    void register_stillThrowsWhenFirebaseRollbackAlsoFails() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false);
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));
        willThrow(mock(FirebaseAuthException.class)).given(firebaseAuth).deleteUser("test-uid");

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(RegistrationFailedException.class)
                .hasMessage("회원가입 중 오류가 발생했습니다");
    }

    @Test
    void register_throwsUnauthorizedWhenTokenInvalid() throws Exception {
        given(firebaseAuth.verifyIdToken("invalid-token"))
                .willThrow(mock(FirebaseAuthException.class));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("invalid-token", request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Firebase 토큰입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsConflictWhenUidExists() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(true);

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 가입된 계정입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_skipsEmailCheckWhenEmailIsNull() throws Exception {
        given(mockToken.getEmail()).willReturn(null);
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false);
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        given(request.getCareers()).willReturn(null);

        authService.register("valid-token", request);

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
    }

    @Test
    void register_throwsConflictWhenNicknameExists() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("duplicated");
        given(userRepository.existsByNickname("duplicated")).willReturn(true);

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 사용 중인 닉네임입니다");

        verify(userRepository, never()).save(any());
    }

    // claim도 바디도 없으면 저장 이전에 400 — 폴백 도입 후에도 최종 방어선은 유지된다
    @Test
    void register_throwsBadRequestWhenPhoneNumberMissingFromBothClaimAndBody() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(mockToken.getClaims()).willReturn(Map.of());

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getPhoneNumber()).willReturn(null);

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("휴대폰 번호가 없습니다");

        verify(userRepository, never()).save(any());
    }

    // [임시] Phone Auth 도입 전까지의 폴백 — claim이 없으면 요청 바디의 전화번호를 사용
    @Test
    void register_fallsBackToRequestPhoneNumberWhenClaimMissing() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(mockToken.getClaims()).willReturn(Map.of());
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(userRepository.existsByPhoneNumber("010-9999-8888")).willReturn(false);
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        given(request.getPhoneNumber()).willReturn("010-9999-8888");
        given(request.getCareers()).willReturn(null);

        authService.register("valid-token", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo("010-9999-8888");
    }

    // claim이 있으면 바디 값보다 우선한다 — Phone Auth 도입 시 코드 수정 없이 전환되도록 보장
    @Test
    void register_prefersClaimOverRequestPhoneNumber() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false);
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        given(request.getCareers()).willReturn(null);

        authService.register("valid-token", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo("010-1234-5678");
    }

    // 빈 토큰은 FirebaseAuthException이 아니라 IllegalArgumentException을 유발한다 — 500이 아닌 401이어야 함
    @Test
    void register_throwsUnauthorizedWhenTokenIsEmpty() throws Exception {
        given(firebaseAuth.verifyIdToken("")).willThrow(new IllegalArgumentException("ID token must not be null or empty"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("", request))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsConflictWhenPhoneNumberExists() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.existsByFirebaseUid("test-uid")).willReturn(false);
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(true);

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 가입된 전화번호입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateFcmToken_updatesTokenWhenUserExists() {
        User user = User.builder().id(1L).build();
        User managedUser = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));

        FCMTokenUpdateRequestDTO request = mock(FCMTokenUpdateRequestDTO.class);
        given(request.getFcmToken()).willReturn("new-fcm-token");

        authService.updateFcmToken(user, request);

        verify(managedUser, times(1)).updateFcmToken("new-fcm-token");
    }

    @Test
    void updateFcmToken_throwsNotFoundWhenUserMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        User user = User.builder().id(99L).build();
        FCMTokenUpdateRequestDTO request = mock(FCMTokenUpdateRequestDTO.class);

        assertThatThrownBy(() -> authService.updateFcmToken(user, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }

    @Test
    void updatePhoneNumber_updatesWhenValidAndDifferent() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);

        User user = User.builder().id(1L).build();
        User managedUser = mock(User.class);
        given(managedUser.getPhoneNumber()).willReturn("010-0000-0000");
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false);

        authService.updatePhoneNumber(user, "valid-token");

        verify(managedUser, times(1)).updatePhoneNumber("010-1234-5678");
    }

    @Test
    void updatePhoneNumber_doesNothingWhenSameAsCurrent() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);

        User user = User.builder().id(1L).build();
        User managedUser = mock(User.class);
        given(managedUser.getPhoneNumber()).willReturn("010-1234-5678");
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));

        authService.updatePhoneNumber(user, "valid-token");

        verify(managedUser, never()).updatePhoneNumber(any());
        verify(userRepository, never()).existsByPhoneNumber(any());
    }

    @Test
    void updatePhoneNumber_throwsBadRequestWhenClaimMissing() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(mockToken.getClaims()).willReturn(Map.of());

        User user = User.builder().id(1L).build();

        assertThatThrownBy(() -> authService.updatePhoneNumber(user, "valid-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("휴대폰 인증 정보가 없습니다");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updatePhoneNumber_throwsConflictWhenPhoneNumberExists() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);

        User user = User.builder().id(1L).build();
        User managedUser = mock(User.class);
        given(managedUser.getPhoneNumber()).willReturn("010-0000-0000");
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(true);

        assertThatThrownBy(() -> authService.updatePhoneNumber(user, "valid-token"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 가입된 전화번호입니다");

        verify(managedUser, never()).updatePhoneNumber(any());
    }

    @Test
    void updatePhoneNumber_throwsUnauthorizedWhenTokenInvalid() throws Exception {
        given(firebaseAuth.verifyIdToken("invalid-token"))
                .willThrow(mock(FirebaseAuthException.class));

        User user = User.builder().id(1L).build();

        assertThatThrownBy(() -> authService.updatePhoneNumber(user, "invalid-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Firebase 토큰입니다");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updatePhoneNumber_throwsNotFoundWhenUserMissing() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token")).willReturn(mockToken);
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        User user = User.builder().id(99L).build();

        assertThatThrownBy(() -> authService.updatePhoneNumber(user, "valid-token"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }

    // 회원 탈퇴 — DB 삭제 커밋 후 Firebase 계정을 지운다
    @Test
    void onUserWithdrawn_deletesFirebaseAccount() throws Exception {
        authService.onUserWithdrawn(new UserWithdrawalEvent(1L, "test-uid"));

        verify(firebaseAuth).deleteUser("test-uid");
    }

    // 탈퇴는 이미 끝났으므로 Firebase 삭제 실패가 호출자에게 예외로 번지면 안 된다(고아 계정은 로그로 추적)
    @Test
    void onUserWithdrawn_doesNotThrowWhenFirebaseDeletionFails() throws Exception {
        willThrow(mock(FirebaseAuthException.class)).given(firebaseAuth).deleteUser("test-uid");

        assertThatCode(() -> authService.onUserWithdrawn(new UserWithdrawalEvent(1L, "test-uid")))
                .doesNotThrowAnyException();
    }

    @Test
    void onUserWithdrawn_ignoresAccountThatAlreadyDoesNotExist() throws Exception {
        FirebaseAuthException notFound = mock(FirebaseAuthException.class);
        given(notFound.getAuthErrorCode()).willReturn(AuthErrorCode.USER_NOT_FOUND);
        willThrow(notFound).given(firebaseAuth).deleteUser("test-uid");

        assertThatCode(() -> authService.onUserWithdrawn(new UserWithdrawalEvent(1L, "test-uid")))
                .doesNotThrowAnyException();
    }
}
