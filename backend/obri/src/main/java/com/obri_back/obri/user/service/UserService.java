package com.obri_back.obri.user.service;

import com.obri_back.obri.global.exception.ConflictException;
import com.obri_back.obri.global.exception.ConflictGuard;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.user.dto.CareerDTO;
import com.obri_back.obri.user.dto.UserPublicProfileDTO;
import com.obri_back.obri.user.dto.UserResponseDTO;
import com.obri_back.obri.user.dto.UserUpdateRequestDTO;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.obri_back.obri.user.repository.CareerRepository;
import com.obri_back.obri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 유저 관련 비즈니스 로직 처리
 * 유저 정보 조회, 수정, 탈퇴 및 내 모집글/지원 목록 조회
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CareerRepository careerRepository;
    private final ApplicationEventPublisher eventPublisher;

    /*
     * 내 정보 조회
     *
     * @param userId 현재 로그인한 유저의 내부 ID
     * @return 유저 정보
     */
    @Transactional(readOnly = true)
    public UserResponseDTO getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다"));
        return UserResponseDTO.from(user);
    }

    /*
     * managed User 재조회 — 다른 도메인 서비스가 detached 엔티티(예: 필터에서 온
     * @AuthenticationPrincipal User)를 넘겨받았을 때 LAZY 컬렉션 접근을 안전하게 하기 위한 진입점.
     * 다른 도메인이 UserRepository를 직접 찌르지 않도록 이 메서드를 경유시킨다.
     *
     * @param userId 재조회할 유저의 내부 ID
     * @return managed 상태의 User 엔티티
     */
    @Transactional(readOnly = true)
    public User getManagedUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다"));
    }

    /*
     * 닉네임으로 managed User 조회 — 닉네임(대소문자 무시 UNIQUE)만 아는 화면에서 신고·차단 대상을 지정할 때 쓰는 진입점.
     * 다른 도메인이 UserRepository를 직접 찌르지 않도록 이 메서드를 경유시킨다.
     *
     * @param nickname 조회할 유저의 닉네임
     * @return managed 상태의 User 엔티티
     */
    @Transactional(readOnly = true)
    public User getManagedUserByNickname(String nickname) {
        return userRepository.findByNicknameIgnoreCase(NicknamePolicy.normalize(nickname))
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다"));
    }

    /*
     * 타인 프로필 조회
     * 공개 프로필이므로 email·phoneNumber는 노출하지 않음
     *
     * @param nickname 조회할 유저의 닉네임
     * @return 공개용 유저 정보
     */
    @Transactional(readOnly = true)
    public UserPublicProfileDTO getUserProfile(String nickname) {
        User user = userRepository.findByNicknameIgnoreCase(NicknamePolicy.normalize(nickname))
                .orElseThrow(() -> new NotFoundException("유저를 찾을 수 없습니다"));
        return UserPublicProfileDTO.from(user);
    }

    /*
     * 내 정보 수정
     * 닉네임·악기를 한 번에 반영 (PUT 방식, 둘 다 필수)
     * careers는 보내면 기존 데이터 전체 삭제 후 새로 insert, null이면 경력만 건드리지 않음
     *
     * @param user    현재 로그인한 유저(필터가 조회한 detached 엔티티일 수 있음 — 내부에서 managed 재조회)
     * @param request 수정 요청 DTO
     * @return 수정된 유저 정보
     */
    @Transactional
    public UserResponseDTO updateMyInfo(User user, UserUpdateRequestDTO request) {
        User managedUser = getManagedUserById(user.getId());

        // 닉네임은 정규화·형식 검증(D3). 대소문자만 바꾸는 변경은 본인 행이라 중복 검사를 건너뛴다
        String nickname = NicknamePolicy.normalizeAndValidate(request.getNickname());
        boolean nicknameChanged = !nickname.equals(managedUser.getNickname());
        if (nicknameChanged && !nickname.equalsIgnoreCase(managedUser.getNickname())) {
            ConflictGuard.requireUnique(
                    userRepository.existsByNicknameIgnoreCase(nickname), "이미 사용 중인 닉네임입니다");
        }

        // 유저 정보 수정
        managedUser.updateInfo(nickname, request.getInstrument());
        if (nicknameChanged) {
            flushNicknameChange();
        }

        // 경력 전체 삭제 후 새로 insert
        if (request.getCareers() != null) {
            careerRepository.deleteByUserId(managedUser.getId());
            careerRepository.saveAll(CareerDTO.toEntities(managedUser, request.getCareers()));
        }

        return UserResponseDTO.from(managedUser);
    }

    // 닉네임 변경을 즉시 flush해 사전 체크를 둘 다 통과한 동시 요청의 UNIQUE 위반(lower(nickname))을 닉네임 409로 바꾼다
    // (flush하지 않으면 위반이 커밋 시점에 터져 원인과 무관한 일반 409 메시지가 된다)
    private void flushNicknameChange() {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("이미 사용 중인 닉네임입니다");
        }
    }

    /*
     * 회원 탈퇴 — 유저가 남긴 데이터를 전부 삭제
     * 다른 도메인의 데이터(모집글·지원서·연습일지)는 UserWithdrawalEvent로 각 도메인이 같은 트랜잭션 안에서 정리하고
     * (유저 행보다 먼저 지워야 FK 위반이 나지 않으므로 이벤트 발행 후에 유저를 삭제), Firebase 계정은 커밋 후
     * auth가 삭제한다. 하나라도 실패하면 트랜잭션이 롤백돼 아무것도 지워지지 않는다.
     *
     * @param user 현재 로그인한 유저(detached일 수 있음 — 내부에서 managed 재조회)
     */
    @Transactional
    public void deleteUser(User user) {
        User managedUser = getManagedUserById(user.getId());
        eventPublisher.publishEvent(new UserWithdrawalEvent(managedUser.getId(), managedUser.getFirebaseUid()));
        userRepository.delete(managedUser);
    }

    /*
     * 여러 유저의 경력을 배치 조회 후 유저별로 그룹화
     * application 도메인(지원자 목록)이 페이지 단위 careers를 N+1 없이 로딩할 때 이 메서드를 경유(BACKLOG.md #21) —
     * 다른 도메인이 CareerRepository를 직접 찌르지 않도록 함
     *
     * @param userIds 조회할 유저 id 목록
     * @return 유저 id별 경력 목록 (경력이 없는 유저는 키 자체가 없음)
     */
    @Transactional(readOnly = true)
    public Map<Long, List<CareerDTO>> getCareersByUserIds(List<Long> userIds) {
        return careerRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        career -> career.getUser().getId(),
                        Collectors.mapping(CareerDTO::from, Collectors.toList())));
    }

    /*
     * 닉네임 중복 체크 — 가입·수정과 같은 규칙으로 정규화·검증한 뒤(대소문자 무시) 중복을 확인한다
     *
     * @param nickname 중복 확인할 닉네임
     * @return 중복 여부
     * @throws com.obri_back.obri.global.exception.BadRequestException 형식 위반·예약어(400)
     */
    @Transactional(readOnly = true)
    public boolean checkNickname(String nickname) {
        return userRepository.existsByNicknameIgnoreCase(NicknamePolicy.normalizeAndValidate(nickname));
    }
}