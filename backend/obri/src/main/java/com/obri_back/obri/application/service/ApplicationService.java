package com.obri_back.obri.application.service;

import com.obri_back.obri.application.dto.AppRequestDTO;
import com.obri_back.obri.application.dto.AppResponseDTO;
import com.obri_back.obri.application.entity.Application;
import com.obri_back.obri.application.entity.ApplicationStatus;
import com.obri_back.obri.application.repository.ApplicationRepository;
import com.obri_back.obri.block.service.BlockService;
import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.global.exception.ConflictException;
import com.obri_back.obri.global.exception.ForbiddenException;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.notification.event.ApplicationResultNotificationEvent;
import com.obri_back.obri.notification.event.NewApplicationNotificationEvent;
import com.obri_back.obri.notification.event.PostDeletedNotificationEvent;
import com.obri_back.obri.notification.event.PostUpdatedNotificationEvent;
import com.obri_back.obri.post.entity.Post;
import com.obri_back.obri.post.entity.PostStatus;
import com.obri_back.obri.post.repository.PostRepository;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.obri_back.obri.user.service.UserService;

import com.obri_back.obri.user.dto.CareerDTO;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/*
Application 관련 비즈니스 로직
- 지원서 제출(지원자 관점)
- 지원서 단건 조회(지원자 관점; 탭하여 들어갔을 때 구체적으로 볼 수 있는 정보들)
- 지원 상태 업데이트 (모집자: ACCEPTED/REJECTED, 지원자: CANCELLED)

- 한 게시글에 대한 지원서 목록 조회(PENDING)
- 모집글 수정·삭제 시 지원자 알림·정리 처리 (Post 도메인 위임 — BACKLOG.md #12)

알림은 전부 NotificationEventListener를 거쳐 커밋 이후(AFTER_COMMIT)에 발송된다.
이 클래스는 NotificationService를 직접 호출하지 않고 ApplicationEventPublisher로 이벤트만 발행한다.
*/

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final PostRepository postRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationAccessPolicy accessPolicy;
    private final BlockService blockService;

    // 지원서 제출
    @Transactional
    public AppResponseDTO submitApplication(User user, AppRequestDTO requestDto) {
        Post post = postRepository.findById(requestDto.getPostId())
            .orElseThrow(() -> new NotFoundException("모집글을 찾을 수 없습니다"));

        // 마감된 모집글 체크
        if (post.getStatus() == PostStatus.CLOSED) {
            throw new BadRequestException("마감된 모집글에는 지원할 수 없습니다");
        }

        // 공연 날짜가 지난 모집글 체크 (인원이 안 찼어도 날짜가 지나면 지원 불가)
        if (post.getEventAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("이미 종료된 공연에는 지원할 수 없습니다");
        }

        // 지원 악기는 요청 값(없으면 프로필 악기)이고, 이 글이 모집하는 악기이며 정원이 남아 있어야 한다(D9, 둘 다 400)
        String instrument = resolveInstrument(user, requestDto);
        post.requireAcceptingInstrument(instrument);

        // 본인 글 지원 체크
        if (post.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("본인 모집글에는 지원할 수 없습니다");
        }

        // 모집자가 차단한 유저는 지원 불가 — 차단 사실을 알려주지 않도록 사유를 구체적으로 밝히지 않는다
        if (blockService.isBlocked(post.getUser().getId(), user.getId())) {
            throw new ForbiddenException("지원할 수 없는 모집글입니다");
        }

        // 중복 지원 체크 (DB UNIQUE 제약의 사전 방어선). 취소한 지원만 다시 지원할 수 있고(D7),
        // 대기·수락·거절·철회된 지원이 있으면 409
        Optional<Application> previous = applicationRepository.findByPostIdAndUserId(post.getId(), user.getId());
        if (previous.isPresent() && previous.get().getStatus() != ApplicationStatus.CANCELLED) {
            throw new ConflictException("이미 지원한 모집글입니다");
        }

        Application application;
        if (previous.isPresent()) {
            // 취소했던 지원 복구 — 같은 행을 PENDING으로 되돌리고 악기·어필 문구를 새 값으로 갱신(변경 감지로 저장)
            application = previous.get();
            application.reapply(instrument, requestDto.getAdditionalInfo());
        } else {
            // 지원 악기를 저장한다(수락 시 모집 악기 정원과 매칭에 사용)
            application = applicationRepository.save(Application.builder()
                .user(user)
                .post(post)
                .instrument(instrument)
                .additionalInfo(requestDto.getAdditionalInfo())
                .status(ApplicationStatus.PENDING)
                .build());
        }

        // 지원 도착 → 모집자(글 작성자)에게 단건 push. AFTER_COMMIT 이후 발송 — 이 트랜잭션이
        // 롤백되면(예: 아래 managedUser 재조회 실패) 이벤트 자체가 버려져 유령 알림이 나가지 않는다
        eventPublisher.publishEvent(new NewApplicationNotificationEvent(
                post.getUser().getFcmToken(), post.getId(), post.getTitle()));

        // user는 FirebaseAuthFilter가 조회한 detached 엔티티라 careers(LAZY) 접근 시
        // LazyInitializationException 발생 — UserService를 경유해 managed 인스턴스로 재조회
        // (UserRepository를 직접 주입하면 도메인 경계를 깨므로 서비스 간 호출로 유지)
        User managedUser = userService.getManagedUserById(user.getId());

        return AppResponseDTO.from(application, managedUser);
    }

    // 지원 악기 결정 — 요청에 있으면 그 값(앞뒤 공백 제거), 없으면 프로필 악기
    private String resolveInstrument(User user, AppRequestDTO requestDto) {
        String requested = requestDto.getInstrument();
        return requested == null || requested.isBlank() ? user.getInstrument() : requested.trim();
    }

    // 한 게시글에 대한 지원서 목록 조회
    // careers는 user.getCareers() lazy 접근 대신 UserService 배치 조회로 채움 — N+1 방지(BACKLOG.md #21)
    @Transactional(readOnly = true)
    public Page<AppResponseDTO> getApplicationsByPostId(Long postId, User user, Pageable pageable) {
         Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("모집글을 찾을 수 없습니다"));

        // 모집자만 조회 가능
        accessPolicy.requireRecruiter(user, post, "모집자만 지원자 목록을 조회할 수 있습니다");

        Page<Application> applications = applicationRepository.findByPostId(postId, pageable);
        Map<Long, List<CareerDTO>> careersByUserId = loadCareersByApplicant(applications);
        return applications.map(application -> AppResponseDTO.from(
                application, application.getUser(),
                careersByUserId.getOrDefault(application.getUser().getId(), List.of())));
    }

    // 아이디로 지원서 목록 조회
    // careers는 user.getCareers() lazy 접근 대신 UserService 배치 조회로 채움 — N+1 방지(BACKLOG.md #21)
    @Transactional(readOnly = true)
    public Page<AppResponseDTO> getApplicationsByUserId(Long userId, Pageable pageable) {
        Page<Application> applications = applicationRepository.findByUserId(userId, pageable);
        Map<Long, List<CareerDTO>> careersByUserId = loadCareersByApplicant(applications);
        return applications.map(application -> AppResponseDTO.from(
                application, application.getUser(),
                careersByUserId.getOrDefault(application.getUser().getId(), List.of())));
    }

    // 페이지 안의 지원자 id를 모아 careers를 한 번에 배치 조회 (UserService 경유 — CareerRepository 직접 의존 금지)
    private Map<Long, List<CareerDTO>> loadCareersByApplicant(Page<Application> applications) {
        List<Long> applicantIds = applications.getContent().stream()
                .map(application -> application.getUser().getId())
                .distinct()
                .collect(Collectors.toList());
        return userService.getCareersByUserIds(applicantIds);
    }

    // 지원서 단건 조회
    @Transactional(readOnly = true)
    public AppResponseDTO getApplication(User user, Long applicationId) {
        
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("지원서를 찾을 수 없습니다"));

        // 모집자 또는 지원자 본인만 조회 가능
        accessPolicy.requireViewer(user, application, "조회 권한이 없습니다");

        return AppResponseDTO.from(application, application.getUser());
    }

    // ── 상태 전이 (명세 Application §2.4) ────────────────────────────
    // 전이별 행위자·출발 상태가 하나로 고정 → 엔드포인트/메서드 단위로 인가를 명확히 강제
    // 수락/철회 시 해당 악기 확정 인원·마감은 Post 도메인에 위임
    // 지원 결과(수락/거절)만 지원자에게 단건 push. AFTER_COMMIT 이후 발송

    // 수락 (모집자, PENDING → ACCEPTED)
    @Transactional
    public void accept(User user, Long id) {
        Application application = findApplicationOrThrow(id);
        accessPolicy.requireRecruiter(user, application, "모집자만 수락 또는 거절할 수 있습니다");
        requirePending(application);
        requireEventNotPassed(application.getPost(), "이미 종료된 공연의 지원은 수락할 수 없습니다");
        application.getPost().confirmInstrument(application.getInstrument());
        application.updateStatus(ApplicationStatus.ACCEPTED);
        // AFTER_COMMIT 이후 발송 — @Version 충돌로 이 트랜잭션이 롤백돼도 유령 "수락" 알림 없음
        eventPublisher.publishEvent(new ApplicationResultNotificationEvent(application.getUser().getFcmToken(), true));
        // 이 수락으로 악기 정원이 차면 같은 악기의 남은 대기 지원은 자동 거절한다(D10, 대기자 유지 없음)
        Post post = application.getPost();
        if (post.isInstrumentClosed(application.getInstrument())) {
            rejectPendingByInstrument(post.getId(), application.getInstrument());
        }
    }

    // 같은 글·같은 악기의 대기(PENDING) 지원을 모두 거절(REJECTED)하고 각 지원자에게 거절 알림 이벤트를 발행한다(D10·D12).
    // 정원 마감(accept)과 수락자 없는 악기 삭제(글 수정)가 같이 쓰도록 한 곳에 둔다. 자동 거절된 지원은 REJECTED라
    // 재지원이 막히며(D7), 이후 수락이 철회돼 자리가 다시 열려도 복구하지 않는다
    @Transactional
    public void rejectPendingByInstrument(Long postId, String instrument) {
        applicationRepository.findByPostIdAndInstrumentAndStatus(postId, instrument, ApplicationStatus.PENDING)
                .forEach(pending -> {
                    pending.updateStatus(ApplicationStatus.REJECTED);
                    eventPublisher.publishEvent(
                            new ApplicationResultNotificationEvent(pending.getUser().getFcmToken(), false));
                });
    }

    // 거절 (모집자, PENDING → REJECTED)
    @Transactional
    public void reject(User user, Long id) {
        Application application = findApplicationOrThrow(id);
        accessPolicy.requireRecruiter(user, application, "모집자만 수락 또는 거절할 수 있습니다");
        requirePending(application);
        application.updateStatus(ApplicationStatus.REJECTED);
        // AFTER_COMMIT 이후 발송
        eventPublisher.publishEvent(new ApplicationResultNotificationEvent(application.getUser().getFcmToken(), false));
    }

    // 취소 (지원자, PENDING → CANCELLED). 결과 알림 없음
    @Transactional
    public void cancel(User user, Long id) {
        Application application = findApplicationOrThrow(id);
        accessPolicy.requireApplicant(user, application, "지원자만 지원을 취소할 수 있습니다");
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BadRequestException("대기 중인 지원만 취소할 수 있습니다");
        }
        application.updateStatus(ApplicationStatus.CANCELLED);
    }

    // 철회 (모집자, ACCEPTED → REVOKED; 확정 취소·자리 재오픈). 결과 알림 없음
    @Transactional
    public void revoke(User user, Long id) {
        Application application = findApplicationOrThrow(id);
        accessPolicy.requireRecruiter(user, application, "모집자만 수락을 철회할 수 있습니다");
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new BadRequestException("수락된 지원만 철회할 수 있습니다");
        }
        requireEventNotPassed(application.getPost(), "이미 종료된 공연의 수락은 철회할 수 없습니다");
        application.getPost().revokeInstrument(application.getInstrument());
        application.updateStatus(ApplicationStatus.REVOKED);
    }

    private Application findApplicationOrThrow(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("지원서를 찾을 수 없습니다"));
    }

    // 공연일이 지난 글의 수락·철회는 막는다(D8). 수동 마감한 글의 수락·거절·철회와 공연이 끝난 글의 거절·취소는 허용한다
    private void requireEventNotPassed(Post post, String message) {
        if (post.getEventAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException(message);
        }
    }

    private void requirePending(Application application) {
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BadRequestException("대기 중인 지원서만 처리할 수 있습니다");
        }
    }

    // 모집글 수정 알림 — Post 도메인에서 호출. 대기·수락 지원자에게 알릴지 여부까지 이 도메인이 결정
    // TODO: 수정 알림의 실제 필요성은 추후 재검토 대상(우선순위 낮음, 논의 2026-07-29)
    @Transactional(readOnly = true)
    public void notifyApplicantsOfPostUpdate(Long postId, String title) {
        List<String> tokens = applicationRepository.findApplicantFcmTokens(postId,
                List.of(ApplicationStatus.PENDING, ApplicationStatus.ACCEPTED));
        eventPublisher.publishEvent(new PostUpdatedNotificationEvent(tokens, postId, title));
    }

    // 모집글 삭제 처리 — Post 도메인에서 호출(Post row 삭제 전 반드시 먼저 호출, FK 순서 보장)
    // ACCEPTED 지원자 토큰 확보 → 지원서 정리 → 삭제 알림까지 전담
    // TODO: 삭제 알림의 실제 필요성은 추후 재검토 대상(우선순위 낮음, 논의 2026-07-29)
    @Transactional
    public void handlePostDeletion(Long postId, String title) {
        List<String> acceptedTokens = applicationRepository.findApplicantFcmTokens(postId, List.of(ApplicationStatus.ACCEPTED));
        applicationRepository.deleteByPostId(postId);
        eventPublisher.publishEvent(new PostDeletedNotificationEvent(acceptedTokens, postId, title));
    }

    // 회원 탈퇴 시 이 유저가 낸 지원서 정리 — UserService가 발행한 UserWithdrawalEvent를 같은 트랜잭션에서 처리
    // (유저 행 삭제보다 먼저 실행돼야 FK 위반이 없다). 수락된 지원은 revoke()와 같이 악기 확정 인원을 되돌려
    // 자리를 다시 열고, 그 뒤 상태와 무관하게 전부 삭제한다. 이 유저가 작성한 모집글의 지원서는 PostService가
    // handlePostDeletion으로 처리한다(본인 글엔 지원할 수 없으므로 두 범위가 겹치지 않는다).
    @EventListener
    @Transactional
    public void onUserWithdrawal(UserWithdrawalEvent event) {
        applicationRepository.findByUserIdAndStatus(event.userId(), ApplicationStatus.ACCEPTED)
                .forEach(application -> application.getPost().revokeInstrument(application.getInstrument()));
        applicationRepository.deleteByUserId(event.userId());
    }

    // 모집글 단건 조회(applicationCount)용 — Post 도메인에서 호출
    @Transactional(readOnly = true)
    public long countApplicationsByPostId(Long postId) {
        return applicationRepository.countByPostId(postId);
    }

    // 모집글 단건 조회(myApplicationStatus·hasApplied)용 — Post 도메인에서 호출. 지원한 적이 없으면 null
    @Transactional(readOnly = true)
    public ApplicationStatus getMyApplicationStatus(Long postId, Long userId) {
        return applicationRepository.findByPostIdAndUserId(postId, userId)
                .map(Application::getStatus)
                .orElse(null);
    }
}
