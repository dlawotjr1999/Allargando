package com.wangnu.allargando.auth.service;

import com.google.firebase.auth.AuthErrorCode;
import com.wangnu.allargando.notification.event.StaleFcmTokensEvent;
import com.wangnu.allargando.user.event.UserWithdrawalEvent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.wangnu.allargando.auth.dto.FCMTokenUpdateRequestDTO;
import com.wangnu.allargando.auth.dto.RegisterRequestDTO;
import com.wangnu.allargando.global.exception.ConflictException;
import com.wangnu.allargando.global.exception.NotFoundException;
import com.wangnu.allargando.global.exception.BadRequestException;
import com.wangnu.allargando.global.exception.UnauthorizedException;
import com.wangnu.allargando.user.dto.CareerDTO;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.repository.CareerRepository;
import com.wangnu.allargando.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    @Mock TransactionTemplate transactionTemplate;

    @InjectMocks AuthService authService;

    private FirebaseToken mockToken;

    @BeforeEach
    void setUp() throws Exception {
        mockToken = mock(FirebaseToken.class);
        lenient().when(mockToken.getUid()).thenReturn("test-uid");
        lenient().when(mockToken.getClaims())
                .thenReturn(Map.of("phone_number", "+821012345678"));
        // TransactionTemplate은 콜백을 그대로 실행하는 것으로 대체(트랜잭션 자체는 통합 테스트 영역)
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    @Test
    void register_savesUserWhenValid() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        given(request.getCareers()).willReturn(null);

        authService.register("valid-token", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo("010-1234-5678");
    }

    // 유저와 경력은 같은 트랜잭션 콜백 안에서 저장된다
    @Test
    void register_savesUserAndCareersInSingleTransaction() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        given(request.getCareers()).willReturn(List.of(
                CareerDTO.builder().organization("오케스트라").contexts("바이올린 파트").build()));

        authService.register("valid-token", request);

        verify(transactionTemplate, times(1)).execute(any());
        verify(careerRepository, times(1)).saveAll(any());
    }

    // D6: 이미 가입된 uid로 다시 호출하면 409가 아니라 기존 결과를 반환하고, 저장·Firebase 삭제는 하지 않는다
    @Test
    void register_returnsExistingResultWhenUidAlreadyRegistered() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.findByFirebaseUid("test-uid"))
                .willReturn(Optional.of(User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).firebaseUid("test-uid").build()));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatCode(() -> authService.register("valid-token", request)).doesNotThrowAnyException();

        verify(userRepository, never()).save(any());
        verify(firebaseAuth, never()).deleteUser(anyString());
    }

    // 저장 실패 시 Firebase 계정을 지우지 않는다 — 클라이언트가 같은 토큰으로 재시도한다(중복이 아닌 제약 위반은 그대로 전파)
    @Test
    void register_doesNotDeleteFirebaseAccountWhenSaveFails() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.save(any(User.class)))
                .willThrow(new DataIntegrityViolationException("value too long"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");

        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(firebaseAuth, never()).deleteUser(anyString());
    }

    // 같은 uid의 동시 요청 중 늦은 쪽: UNIQUE 위반 뒤 재조회로 먼저 성공한 행을 찾아 성공으로 응답한다
    @Test
    void register_returnsExistingWhenConcurrentRequestWonUidRace() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.findByFirebaseUid("test-uid"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).firebaseUid("test-uid").build()));
        given(userRepository.save(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");

        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        assertThatCode(() -> authService.register("valid-token", request)).doesNotThrowAnyException();

        verify(firebaseAuth, never()).deleteUser(anyString());
    }

    // 다른 uid가 전화번호를 먼저 가져간 경쟁: 사전 체크는 통과했지만 UNIQUE 위반 뒤 재검사에서 구체적인 409
    @Test
    void register_throwsConflictWhenPhoneNumberRaceLoses() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false).willReturn(true);
        given(userRepository.save(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");

        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 가입된 전화번호입니다");

        verify(firebaseAuth, never()).deleteUser(anyString());
    }

    // 닉네임 경쟁도 같은 방식으로 구체적인 409
    @Test
    void register_throwsConflictWhenNicknameRaceLoses() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.existsByNicknameIgnoreCase("tester")).willReturn(false).willReturn(true);
        given(userRepository.save(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");

        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 사용 중인 닉네임입니다");
    }

    // D3: 형식 위반·예약어 닉네임은 중복 검사·저장 이전에 400
    @Test
    void register_throwsBadRequestWhenNicknameInvalid() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("a/b");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");

        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("사용할 수 없는 닉네임입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsBadRequestWhenNicknameIsReserved() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("Admin");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");

        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    // 중복 검사와 저장에는 정규화(trim+NFC)된 닉네임을 쓴다
    @Test
    void register_savesNormalizedNickname() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn(" " + java.text.Normalizer.normalize("한글", java.text.Normalizer.Form.NFD) + " ");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        given(request.getCareers()).willReturn(null);

        authService.register("valid-token", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("한글");
        verify(userRepository).existsByNicknameIgnoreCase("한글");
    }

    // checkRevoked=true: 폐기·삭제된 계정의 토큰은 거절된다(탈퇴 직후 토큰으로 유령 계정이 생기는 것을 막음)
    @Test
    void register_verifiesTokenWithRevocationCheckAndRejectsRevokedToken() throws Exception {
        given(firebaseAuth.verifyIdToken("revoked-token", true)).willThrow(mock(FirebaseAuthException.class));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("revoked-token", request))
                .isInstanceOf(UnauthorizedException.class);

        verify(firebaseAuth).verifyIdToken("revoked-token", true);
        verify(userRepository, never()).save(any());
    }

    // Firebase에 닿지 못해 토큰을 판정하지 못한 것은 토큰 문제가 아니다 → 401이 아니라 503
    @Test
    void register_throwsServiceUnavailableWhenFirebaseIsDown() throws Exception {
        FirebaseAuthException outage = mock(FirebaseAuthException.class);
        given(outage.getAuthErrorCode()).willReturn(null);
        given(outage.getErrorCode()).willReturn(com.google.firebase.ErrorCode.UNAVAILABLE);
        given(firebaseAuth.verifyIdToken("any-token", true)).willThrow(outage);

        assertThatThrownBy(() -> authService.register("any-token", mock(RegisterRequestDTO.class)))
                .isInstanceOf(com.wangnu.allargando.global.exception.ServiceUnavailableException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsUnauthorizedWhenTokenInvalid() throws Exception {
        given(firebaseAuth.verifyIdToken("invalid-token", true))
                .willThrow(mock(FirebaseAuthException.class));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("invalid-token", request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Firebase 토큰입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsConflictWhenNicknameExists() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("duplicated");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        given(userRepository.existsByNicknameIgnoreCase("duplicated")).willReturn(true);

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 사용 중인 닉네임입니다");

        verify(userRepository, never()).save(any());
    }

    // 전화 인증을 거치지 않은 토큰(phone_number claim 없음)은 저장 이전에 400 — 요청 바디의 번호는 받지 않는다
    @Test
    void register_throwsBadRequestWhenPhoneClaimMissing() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(mockToken.getClaims()).willReturn(Map.of());

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("휴대폰 인증 정보가 없습니다");

        verify(userRepository, never()).save(any());
    }

    // 국내 휴대폰 번호가 아닌 번호(예: 미국 +1)로 인증한 토큰은 저장 이전에 400
    @Test
    void register_throwsBadRequestWhenPhoneIsNotKoreanMobile() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(mockToken.getClaims()).willReturn(Map.of("phone_number", "+14155550123"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("국내 휴대폰 번호로만 인증할 수 있습니다");

        verify(userRepository, never()).save(any());
    }

    // 빈 토큰은 FirebaseAuthException이 아니라 IllegalArgumentException을 유발한다 — 500이 아닌 401이어야 함
    @Test
    void register_throwsUnauthorizedWhenTokenIsEmpty() throws Exception {
        given(firebaseAuth.verifyIdToken("", true)).willThrow(new IllegalArgumentException("ID token must not be null or empty"));

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);

        assertThatThrownBy(() -> authService.register("", request))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsConflictWhenPhoneNumberExists() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(true);

        RegisterRequestDTO request = mock(RegisterRequestDTO.class);
        given(request.getNickname()).willReturn("tester");
        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");

        org.mockito.Mockito.lenient().when(request.getName()).thenReturn("홍길동");
        assertThatThrownBy(() -> authService.register("valid-token", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 가입된 전화번호입니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateFcmToken_updatesTokenWhenUserExists() {
        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();
        User managedUser = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));

        FCMTokenUpdateRequestDTO request = mock(FCMTokenUpdateRequestDTO.class);
        given(request.getFcmToken()).willReturn("new-fcm-token");

        authService.updateFcmToken(user, request);

        verify(managedUser, times(1)).updateFcmToken("new-fcm-token");
        // 같은 기기의 이전 계정이 쥔 같은 토큰은 먼저 비운다
        verify(userRepository).clearFcmTokenOfOthers("new-fcm-token", 1L);
    }

    @Test
    void clearFcmToken_setsTokenNull() {
        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();
        User managedUser = mock(User.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));

        authService.clearFcmToken(user);

        verify(managedUser).updateFcmToken(null);
    }

    @Test
    void clearFcmToken_throwsNotFoundWhenUserMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.clearFcmToken(User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(99L).build()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateFcmToken_throwsNotFoundWhenUserMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(99L).build();
        FCMTokenUpdateRequestDTO request = mock(FCMTokenUpdateRequestDTO.class);

        assertThatThrownBy(() -> authService.updateFcmToken(user, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("유저를 찾을 수 없습니다");
    }

    @Test
    void updatePhoneNumber_updatesWhenValidAndDifferent() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);

        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();
        User managedUser = mock(User.class);
        given(managedUser.getPhoneNumber()).willReturn("010-0000-0000");
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));
        given(userRepository.existsByPhoneNumber("010-1234-5678")).willReturn(false);

        authService.updatePhoneNumber(user, "valid-token");

        verify(managedUser, times(1)).updatePhoneNumber("010-1234-5678");
    }

    @Test
    void updatePhoneNumber_doesNothingWhenSameAsCurrent() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);

        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();
        User managedUser = mock(User.class);
        given(managedUser.getPhoneNumber()).willReturn("010-1234-5678");
        given(userRepository.findById(1L)).willReturn(Optional.of(managedUser));

        authService.updatePhoneNumber(user, "valid-token");

        verify(managedUser, never()).updatePhoneNumber(any());
        verify(userRepository, never()).existsByPhoneNumber(any());
    }

    @Test
    void updatePhoneNumber_throwsBadRequestWhenClaimMissing() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(mockToken.getClaims()).willReturn(Map.of());

        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();

        assertThatThrownBy(() -> authService.updatePhoneNumber(user, "valid-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("휴대폰 인증 정보가 없습니다");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updatePhoneNumber_throwsConflictWhenPhoneNumberExists() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);

        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();
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
        given(firebaseAuth.verifyIdToken("invalid-token", true))
                .willThrow(mock(FirebaseAuthException.class));

        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(1L).build();

        assertThatThrownBy(() -> authService.updatePhoneNumber(user, "invalid-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 Firebase 토큰입니다");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void updatePhoneNumber_throwsNotFoundWhenUserMissing() throws Exception {
        given(firebaseAuth.verifyIdToken("valid-token", true)).willReturn(mockToken);
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        User user = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now()).id(99L).build();

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

    // Firebase 예외가 아닌 예상 밖의 실패(앱 미초기화·네트워크 등)도 탈퇴와 분리한다
    @Test
    void onUserWithdrawn_doesNotThrowWhenUnexpectedRuntimeExceptionOccurs() throws Exception {
        willThrow(new IllegalStateException("FirebaseApp is not initialized")).given(firebaseAuth).deleteUser("test-uid");

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

    // FCM이 죽은 토큰(앱 삭제 등으로 더는 쓸 수 없는 토큰)이라고 알려 오면 그 토큰을 DB에서 비운다
    @Test
    void onStaleFcmTokens_clearsReportedTokens() {
        given(userRepository.clearFcmTokens(List.of("dead-1", "dead-2"))).willReturn(2);

        authService.onStaleFcmTokens(new StaleFcmTokensEvent(List.of("dead-1", "dead-2")));

        verify(userRepository).clearFcmTokens(List.of("dead-1", "dead-2"));
    }
}
