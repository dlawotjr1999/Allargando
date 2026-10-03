package com.obri_back.obri.application.service;

import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.obri_back.obri.application.dto.AppRequestDTO;
import com.obri_back.obri.application.dto.AppResponseDTO;
import com.obri_back.obri.application.entity.Application;
import com.obri_back.obri.application.entity.ApplicationStatus;
import com.obri_back.obri.application.repository.ApplicationRepository;
import com.obri_back.obri.block.service.BlockService;
import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.global.exception.ForbiddenException;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.notification.event.ApplicationResultNotificationEvent;
import com.obri_back.obri.notification.event.NewApplicationNotificationEvent;
import com.obri_back.obri.notification.event.PostDeletedNotificationEvent;
import com.obri_back.obri.notification.event.PostUpdatedNotificationEvent;
import com.obri_back.obri.post.entity.Post;
import com.obri_back.obri.post.entity.PostStatus;
import com.obri_back.obri.post.repository.PostRepository;
import com.obri_back.obri.user.dto.CareerDTO;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock PostRepository postRepository;
    @Mock UserService userService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ApplicationAccessPolicy accessPolicy;
    @Mock BlockService blockService;

    @InjectMocks ApplicationService applicationService;

    private User applicant;
    private User recruiter;
    private Post post;

    @BeforeEach
    void setUp() {
        applicant = User.builder()
                .id(1L).nickname("applicant").firebaseUid("applicant-uid").build();

        recruiter = User.builder()
                .id(2L).nickname("recruiter").firebaseUid("recruiter-uid").build();

        post = mock(Post.class);
    }

    // ── 지원서 제출 ──────────────────────────────────

    @Test
    void submitApplication_savesWhenValid() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(post.getId()).willReturn(10L);
        given(post.getStatus()).willReturn(PostStatus.OPEN);
        given(post.getEventAt()).willReturn(LocalDateTime.now().plusDays(1));
        given(post.getUser()).willReturn(recruiter);
        given(applicationRepository.save(any(Application.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(userService.getManagedUserById(applicant.getId())).willReturn(applicant);

        AppRequestDTO request = AppRequestDTO.from(10L, "추가 정보");

        applicationService.submitApplication(applicant, request);

        verify(applicationRepository, times(1)).save(any(Application.class));
        // 지원 도착 시 모집자에게 알림 발송 위임 — AFTER_COMMIT까지 미루기 위해 이벤트로 발행
        verify(eventPublisher, times(1)).publishEvent(any(NewApplicationNotificationEvent.class));
    }

    // user는 FirebaseAuthFilter가 조회한 detached 엔티티라 careers(LAZY) 접근 시
    // LazyInitializationException 발생 — 응답 조립 전 UserService를 통해 managed 인스턴스로 재조회하는지 검증
    // (UserRepository를 직접 주입하면 도메인 경계를 깨므로 UserService를 경유)
    @Test
    void submitApplication_refetchesManagedUserViaUserService_beforeBuildingResponse() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(post.getId()).willReturn(10L);
        given(post.getStatus()).willReturn(PostStatus.OPEN);
        given(post.getEventAt()).willReturn(LocalDateTime.now().plusDays(1));
        given(post.getUser()).willReturn(recruiter);
        given(applicationRepository.save(any(Application.class)))
                .willAnswer(inv -> inv.getArgument(0));

        User managedApplicant = User.builder()
                .id(applicant.getId()).nickname("managed-applicant").firebaseUid("applicant-uid").build();
        given(userService.getManagedUserById(applicant.getId())).willReturn(managedApplicant);

        AppRequestDTO request = AppRequestDTO.from(10L, "추가 정보");

        AppResponseDTO response = applicationService.submitApplication(applicant, request);

        // 응답이 재조회한 managedApplicant(닉네임 다름)로 조립됐는지 확인 — 원본 detached applicant를 그대로 썼다면 실패
        org.assertj.core.api.Assertions.assertThat(response.getApplicant().getNickname()).isEqualTo("managed-applicant");
        verify(userService, times(1)).getManagedUserById(applicant.getId());
    }

    @Test
    void submitApplication_throwsBadRequestWhenClosed() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(post.getStatus()).willReturn(PostStatus.CLOSED);

        AppRequestDTO request = AppRequestDTO.from(10L, null);

        assertThatThrownBy(() -> applicationService.submitApplication(applicant, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("마감된 모집글에는 지원할 수 없습니다");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_throwsBadRequestWhenEventAtPassed() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(post.getStatus()).willReturn(PostStatus.OPEN);
        given(post.getEventAt()).willReturn(LocalDateTime.now().minusDays(1));

        AppRequestDTO request = AppRequestDTO.from(10L, null);

        assertThatThrownBy(() -> applicationService.submitApplication(applicant, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("이미 종료된 공연에는 지원할 수 없습니다");

        verify(applicationRepository, never()).save(any());
    }

    // BACKLOG.md #23: 이미 정원이 마감된 악기는 accept() 시점이 아니라 지원 시점에 사전 차단
    @Test
    void submitApplication_throwsBadRequestWhenInstrumentClosed() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(post.getStatus()).willReturn(PostStatus.PARTIALLY_CLOSED);
        given(post.getEventAt()).willReturn(LocalDateTime.now().plusDays(1));
        given(post.isInstrumentClosed(any())).willReturn(true);

        AppRequestDTO request = AppRequestDTO.from(10L, null);

        assertThatThrownBy(() -> applicationService.submitApplication(applicant, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("이미 정원이 마감된 악기입니다");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_throwsForbiddenWhenOwnPost() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(post.getStatus()).willReturn(PostStatus.OPEN);
        given(post.getEventAt()).willReturn(LocalDateTime.now().plusDays(1));
        given(post.getUser()).willReturn(recruiter);

        AppRequestDTO request = AppRequestDTO.from(10L, null);

        assertThatThrownBy(() -> applicationService.submitApplication(recruiter, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인 모집글에는 지원할 수 없습니다");

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submitApplication_throwsNotFoundWhenPostMissing() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        AppRequestDTO request = AppRequestDTO.from(99L, null);

        assertThatThrownBy(() -> applicationService.submitApplication(applicant, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("모집글을 찾을 수 없습니다");
    }

    // ── 지원자 목록 조회 ──────────────────────────────────

    // CLAUDE.md §8: 인가 판단은 ApplicationAccessPolicy로 위임 — 서비스는 정책이 던진 예외를 그대로 전파하는지만 검증
    // (인가 판단 로직 자체는 ApplicationAccessPolicyTest에서 검증)
    @Test
    void getApplicationsByPostId_throwsForbiddenWhenNotOwner() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        doThrow(new ForbiddenException("모집자만 지원자 목록을 조회할 수 있습니다"))
                .when(accessPolicy).requireRecruiter(applicant, post, "모집자만 지원자 목록을 조회할 수 있습니다");

        assertThatThrownBy(() -> applicationService.getApplicationsByPostId(
                10L, applicant, org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("모집자만 지원자 목록을 조회할 수 있습니다");
    }

    // BACKLOG.md #21: careers는 user.getCareers() lazy 접근 대신 UserService의 배치 조회로 채워지는지 검증
    // (CareerRepository 직접 의존 없이 UserService를 경유 — 도메인 경계 유지)
    @Test
    void getApplicationsByPostId_populatesApplicantCareersFromBatchLoadedMap() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        Application app = Application.builder()
                .id(100L).user(applicant).post(post).instrument("바이올린").status(ApplicationStatus.PENDING).build();
        Page<Application> page = new PageImpl<>(List.of(app));
        given(applicationRepository.findByPostId(eq(10L), any())).willReturn(page);

        CareerDTO career = CareerDTO.builder().id(1L).organization("서울시향").contexts("연주").build();
        given(userService.getCareersByUserIds(List.of(applicant.getId())))
                .willReturn(Map.of(applicant.getId(), List.of(career)));

        Page<AppResponseDTO> result = applicationService.getApplicationsByPostId(
                10L, recruiter, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).getApplicant().getCareers()).containsExactly(career);
        verify(userService, times(1)).getCareersByUserIds(List.of(applicant.getId()));
    }

    @Test
    void getApplicationsByPostId_returnsEmptyCareersWhenApplicantHasNone() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        Application app = Application.builder()
                .id(100L).user(applicant).post(post).instrument("바이올린").status(ApplicationStatus.PENDING).build();
        Page<Application> page = new PageImpl<>(List.of(app));
        given(applicationRepository.findByPostId(eq(10L), any())).willReturn(page);
        given(userService.getCareersByUserIds(List.of(applicant.getId()))).willReturn(Map.of());

        Page<AppResponseDTO> result = applicationService.getApplicationsByPostId(
                10L, recruiter, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).getApplicant().getCareers()).isEmpty();
    }

    @Test
    void getApplicationsByUserId_populatesApplicantCareersFromBatchLoadedMap() {
        Application app = Application.builder()
                .id(100L).user(applicant).post(post).instrument("바이올린").status(ApplicationStatus.PENDING).build();
        Page<Application> page = new PageImpl<>(List.of(app));
        given(applicationRepository.findByUserId(eq(applicant.getId()), any())).willReturn(page);

        CareerDTO career = CareerDTO.builder().id(1L).organization("서울시향").contexts("연주").build();
        given(userService.getCareersByUserIds(List.of(applicant.getId())))
                .willReturn(Map.of(applicant.getId(), List.of(career)));

        Page<AppResponseDTO> result = applicationService.getApplicationsByUserId(
                applicant.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).getApplicant().getCareers()).containsExactly(career);
    }

    // ── 상태 변경 ──────────────────────────────────

    private Application buildApplication(ApplicationStatus status) {
        return Application.builder()
                .id(100L)
                .user(applicant)
                .post(post)
                .instrument("바이올린")
                .status(status)
                .build();
    }

    @Test
    void accept_confirmsInstrumentAndNotifiesWhenRecruiter() {
        Application app = buildApplication(ApplicationStatus.PENDING);
        given(applicationRepository.findById(100L)).willReturn(Optional.of(app));

        applicationService.accept(recruiter, 100L);

        verify(post, times(1)).confirmInstrument("바이올린");
        verify(eventPublisher, times(1)).publishEvent(new ApplicationResultNotificationEvent(null, true));
        org.assertj.core.api.Assertions.assertThat(app.getStatus()).isEqualTo(ApplicationStatus.ACCEPTED);
    }

    @Test
    void reject_setsRejectedAndNotifiesWhenRecruiter() {
        Application app = buildApplication(ApplicationStatus.PENDING);
        given(applicationRepository.findById(100L)).willReturn(Optional.of(app));

        applicationService.reject(recruiter, 100L);

        verify(eventPublisher, times(1)).publishEvent(new ApplicationResultNotificationEvent(null, false));
        verify(post, never()).confirmInstrument(any());
        org.assertj.core.api.Assertions.assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void revoke_releasesInstrumentWhenRecruiterAndAccepted() {
        Application app = buildApplication(ApplicationStatus.ACCEPTED);
        given(applicationRepository.findById(100L)).willReturn(Optional.of(app));

        applicationService.revoke(recruiter, 100L);

        verify(post, times(1)).revokeInstrument("바이올린");
        org.assertj.core.api.Assertions.assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REVOKED);
    }

    @Test
    void accept_throwsForbiddenWhenApplicant() {
        Application app = buildApplication(ApplicationStatus.PENDING);
        given(applicationRepository.findById(100L)).willReturn(Optional.of(app));
        doThrow(new ForbiddenException("모집자만 수락 또는 거절할 수 있습니다"))
                .when(accessPolicy).requireRecruiter(applicant, app, "모집자만 수락 또는 거절할 수 있습니다");

        assertThatThrownBy(() -> applicationService.accept(applicant, 100L))
                .isInstanceOf(ForbiddenException.class);

        verify(post, never()).confirmInstrument(any());
    }

    @Test
    void cancel_throwsBadRequestWhenNonPending() {
        Application app = buildApplication(ApplicationStatus.ACCEPTED);
        given(applicationRepository.findById(100L)).willReturn(Optional.of(app));

        assertThatThrownBy(() -> applicationService.cancel(applicant, 100L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void revoke_throwsBadRequestWhenNonAccepted() {
        Application app = buildApplication(ApplicationStatus.PENDING);
        given(applicationRepository.findById(100L)).willReturn(Optional.of(app));

        assertThatThrownBy(() -> applicationService.revoke(recruiter, 100L))
                .isInstanceOf(BadRequestException.class);

        verify(post, never()).revokeInstrument(any());
    }

    // ── Post 도메인에서 호출하는 굵은 단위 메서드 (BACKLOG.md #12) ──────────────────────
    // Post는 "무슨 일이 있었는지"만 알리고, 지원자에게 어떤 의미인지·알릴지는 이 도메인이 결정

    @Test
    void notifyApplicantsOfPostUpdate_sendsToPendingAndAcceptedApplicants() {
        given(applicationRepository.findApplicantFcmTokens(10L,
                java.util.List.of(ApplicationStatus.PENDING, ApplicationStatus.ACCEPTED)))
                .willReturn(java.util.List.of("token-a", "token-b"));

        applicationService.notifyApplicantsOfPostUpdate(10L, "수정된 제목");

        verify(eventPublisher, times(1)).publishEvent(
                new PostUpdatedNotificationEvent(java.util.List.of("token-a", "token-b"), 10L, "수정된 제목"));
    }

    @Test
    void handlePostDeletion_deletesApplicationsThenNotifiesAcceptedApplicantsInOrder() {
        given(applicationRepository.findApplicantFcmTokens(10L, java.util.List.of(ApplicationStatus.ACCEPTED)))
                .willReturn(java.util.List.of("accepted-token"));

        applicationService.handlePostDeletion(10L, "현악 앙상블 단원 모집");

        org.mockito.InOrder inOrder = inOrder(applicationRepository, eventPublisher);
        inOrder.verify(applicationRepository).findApplicantFcmTokens(10L, java.util.List.of(ApplicationStatus.ACCEPTED));
        inOrder.verify(applicationRepository).deleteByPostId(10L);
        inOrder.verify(eventPublisher).publishEvent(
                new PostDeletedNotificationEvent(java.util.List.of("accepted-token"), 10L, "현악 앙상블 단원 모집"));
    }

    // 회원 탈퇴 — 수락된 지원은 악기 확정 인원을 되돌려 자리를 다시 연 뒤, 이 유저의 지원서를 전부 삭제한다
    @Test
    void onUserWithdrawal_revokesAcceptedSlotsThenDeletesAllApplicationsOfUser() {
        Post acceptedPost = mock(Post.class);
        Application accepted = mock(Application.class);
        given(accepted.getPost()).willReturn(acceptedPost);
        given(accepted.getInstrument()).willReturn("바이올린");
        given(applicationRepository.findByUserIdAndStatus(1L, ApplicationStatus.ACCEPTED))
                .willReturn(List.of(accepted));

        applicationService.onUserWithdrawal(new UserWithdrawalEvent(1L, "applicant-uid"));

        org.mockito.InOrder inOrder = inOrder(acceptedPost, applicationRepository);
        inOrder.verify(acceptedPost).revokeInstrument("바이올린");
        inOrder.verify(applicationRepository).deleteByUserId(1L);
    }

    @Test
    void onUserWithdrawal_justDeletesWhenNoAcceptedApplications() {
        given(applicationRepository.findByUserIdAndStatus(1L, ApplicationStatus.ACCEPTED))
                .willReturn(List.of());

        applicationService.onUserWithdrawal(new UserWithdrawalEvent(1L, "applicant-uid"));

        verify(applicationRepository).deleteByUserId(1L);
    }

    // 모집자가 차단한 유저는 지원할 수 없다 — 차단 사실을 드러내지 않는 일반 메시지로 403
    @Test
    void submitApplication_throwsForbiddenWhenRecruiterBlockedApplicant() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(post.getStatus()).willReturn(PostStatus.OPEN);
        given(post.getEventAt()).willReturn(LocalDateTime.now().plusDays(1));
        given(post.getUser()).willReturn(recruiter);
        given(blockService.isBlocked(recruiter.getId(), applicant.getId())).willReturn(true);

        assertThatThrownBy(() -> applicationService.submitApplication(applicant, AppRequestDTO.from(10L, "지원합니다")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("지원할 수 없는 모집글입니다");

        verify(applicationRepository, never()).save(any(Application.class));
    }
}
