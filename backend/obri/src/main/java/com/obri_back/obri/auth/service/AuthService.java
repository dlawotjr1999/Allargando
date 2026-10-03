package com.obri_back.obri.auth.service;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.obri_back.obri.auth.dto.FCMTokenUpdateRequestDTO;
import com.obri_back.obri.auth.dto.RegisterRequestDTO;
import com.obri_back.obri.auth.dto.RegisterResponseDTO;
import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.global.exception.ConflictGuard;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.global.exception.UnauthorizedException;
import com.obri_back.obri.user.dto.CareerDTO;
import com.obri_back.obri.user.entity.Career;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.repository.CareerRepository;
import com.obri_back.obri.user.repository.UserRepository;
import com.obri_back.obri.user.service.NicknamePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/*
 * 인증 관련 비즈니스 로직 처리
 * Firebase Authentication과 DB 유저 정보를 연동
 * 회원가입 시 Firebase UID로 유저를 식별하고 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final CareerRepository careerRepository;
    private final TransactionTemplate transactionTemplate;

    /*
     * 회원가입 (멱등, D6)
     * Firebase ID Token에서 UID·이메일·전화번호를 추출해 유저와 경력을 한 트랜잭션으로 저장
     * 이미 가입된 firebase_uid면 409 대신 기존 행의 결과를 그대로 반환(요청 바디는 무시)
     * → 더블탭·네트워크 재시도에도 안전하고, 실패 시 클라이언트는 같은 토큰으로 재시도하면 된다
     * Firebase 계정은 지우지 않는다(보상 삭제 없음) — 지우면 동시 요청의 정상 가입 계정까지 사라진다
     *
     * 메서드 전체에 @Transactional을 걸지 않는다: UNIQUE 위반이 난 트랜잭션은 롤백 대상이라
     * 그 세션으로는 같은 uid 행을 다시 조회할 수 없다. 저장(유저+경력)만 TransactionTemplate으로 묶고
     * 위반 시 트랜잭션 밖에서 재조회해 "같은 uid의 동시 가입(성공)"과 "다른 필드 중복(409)"을 가른다.
     *
     * @param idToken Firebase ID Token (Authorization 헤더에서 추출)
     * @param request 회원가입 요청 DTO
     * @return 가입 시각(createdAt)만 포함한 응답
     */
    public RegisterResponseDTO register(String idToken, RegisterRequestDTO request) {
        FirebaseToken decodedToken = verifyToken(idToken);

        String firebaseUid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String phoneNumber = resolvePhoneNumber(decodedToken, request);

        // 멱등: 이미 가입된 uid면 기존 결과 반환
        Optional<User> existing = userRepository.findByFirebaseUid(firebaseUid);
        if (existing.isPresent()) {
            return RegisterResponseDTO.from(existing.get());
        }
        // 닉네임은 정규화·형식 검증(D3) 후 그 값으로 중복 검사·저장한다
        String nickname = NicknamePolicy.normalizeAndValidate(request.getNickname());
        requireNoDuplicateFields(email, phoneNumber, nickname);

        User user = User.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .nickname(nickname)
                .phoneNumber(phoneNumber)
                .instrument(request.getInstrument())
                .build();

        try {
            User saved = transactionTemplate.execute(status -> saveUserWithCareers(user, request));
            return RegisterResponseDTO.from(saved);
        } catch (DataIntegrityViolationException e) {
            // 사전 체크를 둘 다 통과한 동시 요청의 UNIQUE 경쟁 — 트랜잭션 밖에서 원인을 구분한다
            Optional<User> concurrent = userRepository.findByFirebaseUid(firebaseUid);
            if (concurrent.isPresent()) {
                return RegisterResponseDTO.from(concurrent.get());
            }
            requireNoDuplicateFields(email, phoneNumber, nickname);
            throw e; // 중복이 아닌 제약 위반(길이 등)은 그대로 올려 GlobalExceptionHandler에 맡긴다
        }
    }

    // 유저와 경력을 한 트랜잭션으로 저장 — 둘 중 하나라도 실패하면 함께 롤백된다
    private User saveUserWithCareers(User user, RegisterRequestDTO request) {
        User saved = userRepository.save(user);
        List<Career> careers = CareerDTO.toEntities(saved, request.getCareers());
        if (!careers.isEmpty()) {
            careerRepository.saveAll(careers);
        }
        return saved;
    }

    // email·전화번호·닉네임 중복 검사 — 사전 체크와 UNIQUE 경쟁 후 원인 구분에 같이 쓴다
    private void requireNoDuplicateFields(String email, String phoneNumber, String nickname) {
        // email은 선택 필드(§3.1) — null이면 existsByEmail(null)이 SQL상 항상 false로 무력화되므로 의미 없는 호출을 스킵
        if (email != null) {
            ConflictGuard.requireUnique(
                    userRepository.existsByEmail(email), "이미 가입된 이메일입니다");
        }
        // 전화번호 중복 확인 (계정 고유성 앵커)
        ConflictGuard.requireUnique(
                userRepository.existsByPhoneNumber(phoneNumber), "이미 가입된 전화번호입니다");
        ConflictGuard.requireUnique(
                userRepository.existsByNicknameIgnoreCase(nickname), "이미 사용 중인 닉네임입니다");
    }

    /*
     * FCM 토큰 갱신
     * 앱 실행 시 클라이언트에서 최신 FCM 토큰을 전송해 DB 업데이트
     *
     * @param user    현재 로그인한 유저(detached일 수 있음 — 내부에서 managed 재조회)
     * @param request FCM 토큰 갱신 요청 DTO
     */
    @Transactional
    public void updateFcmToken(User user, FCMTokenUpdateRequestDTO request) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다"));

        managedUser.updateFcmToken(request.getFcmToken());
    }

    /*
     * 전화번호 갱신
     * register()와 동일하게 Authorization 헤더의 Firebase ID Token을 재검증해
     * 그 안의 phone_number claim만 신뢰(요청 바디는 받지 않음). 현재 번호와 같으면 아무 것도 하지 않음
     *
     * @param user    현재 로그인한 유저(detached일 수 있음 — 내부에서 managed 재조회)
     * @param idToken Firebase ID Token (Authorization 헤더에서 추출)
     */
    @Transactional
    public void updatePhoneNumber(User user, String idToken) {
        FirebaseToken decodedToken = verifyToken(idToken);
        String phoneNumber = extractPhoneNumberClaim(decodedToken);

        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다"));

        if (phoneNumber.equals(managedUser.getPhoneNumber())) {
            return;
        }

        ConflictGuard.requireUnique(
                userRepository.existsByPhoneNumber(phoneNumber), "이미 가입된 전화번호입니다");

        managedUser.updatePhoneNumber(phoneNumber);
    }

    /*
     * 회원 탈퇴 후 Firebase 계정 삭제 — DB 삭제가 커밋된 뒤에만 실행(AFTER_COMMIT)
     * 롤백되면 이벤트가 버려져 DB엔 유저가 남았는데 Firebase 계정만 사라지는 일이 없다.
     * 삭제에 실패해도 탈퇴 자체는 이미 끝났으므로 예외를 던지지 않고 로그로만 남긴다(고아 계정 추적용).
     * 이미 없는 계정(USER_NOT_FOUND)은 목적이 달성된 상태라 조용히 넘어간다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserWithdrawn(UserWithdrawalEvent event) {
        try {
            firebaseAuth.deleteUser(event.firebaseUid());
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return;
            }
            log.error("탈퇴 후 Firebase 계정 삭제 실패 — 고아 계정 발생 (firebaseUid={})", event.firebaseUid(), e);
        }
    }

    // Firebase 토큰 검증 및 디코딩
    // IllegalArgumentException까지 잡는 이유: verifyIdToken은 토큰이 비어 있으면 FirebaseAuthException이
    // 아니라 IllegalArgumentException을 던진다. 놓치면 401이어야 할 요청이 500으로 새어 나간다.
    private FirebaseToken verifyToken(String idToken) {
        try {
            return firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw new UnauthorizedException("유효하지 않은 Firebase 토큰입니다");
        }
    }

    /*
     * 회원가입 시 전화번호 결정 — claim 우선, 없으면 요청 바디 폴백
     *
     * [임시] 원 설계는 claim만 신뢰하는 것이나(§3.1), Phone Auth가 Expo Go에서 동작하지 않아
     * 전화 인증 도입이 출시 전으로 연기됐다. claim을 먼저 보는 순서는 그대로 두었으므로,
     * 나중에 Phone Auth를 붙이면 이 메서드를 고치지 않아도 자동으로 claim이 우선한다.
     * 전화 인증 도입이 끝나면 아래 폴백 분기와 RegisterRequestDTO.phoneNumber를 함께 제거할 것.
     */
    private String resolvePhoneNumber(FirebaseToken decodedToken, RegisterRequestDTO request) {
        Object phoneClaim = decodedToken.getClaims().get("phone_number");
        if (phoneClaim != null) {
            return phoneClaim.toString();
        }

        String phoneNumber = request.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BadRequestException("휴대폰 번호가 없습니다");
        }
        return phoneNumber;
    }

    // 검증된 토큰에서 phone_number claim 추출 (Firebase Admin SDK에 전용 getter 없어 claims map에서 직접 조회)
    // 전화번호 변경(PATCH /api/auth/phone-number)은 폴백 없이 claim만 신뢰한다 — 변경은 가입과 달리
    // 이미 인증된 사용자의 행위라, 바디 값을 받아주면 남의 번호로 덮어쓸 수 있다
    private String extractPhoneNumberClaim(FirebaseToken decodedToken) {
        Object phoneClaim = decodedToken.getClaims().get("phone_number");
        if (phoneClaim == null) {
            throw new BadRequestException("휴대폰 인증 정보가 없습니다");
        }
        return phoneClaim.toString();
    }
}