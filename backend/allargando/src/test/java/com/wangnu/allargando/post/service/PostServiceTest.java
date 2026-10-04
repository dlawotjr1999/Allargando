package com.wangnu.allargando.post.service;

import com.wangnu.allargando.user.event.UserWithdrawalEvent;
import com.wangnu.allargando.application.service.ApplicationService;
import com.wangnu.allargando.global.exception.BadRequestException;
import com.wangnu.allargando.global.exception.ForbiddenException;
import com.wangnu.allargando.global.exception.NotFoundException;
import com.wangnu.allargando.notification.event.NewPostNotificationEvent;
import com.wangnu.allargando.post.dto.PostCreateRequestDTO;
import com.wangnu.allargando.post.dto.PostDetailResponseDTO;
import com.wangnu.allargando.post.dto.PostResponseDTO;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.post.entity.PostStatus;
import com.wangnu.allargando.post.repository.PostRepository;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private ApplicationService applicationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private PostService postService;

    private User owner;
    private User other;
    private PostCreateRequestDTO request;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(1L)
                .email("test@test.com")
                .firebaseUid("test-uid")
                .phoneNumber("010-1234-5678")
                .nickname("tester")
                .instrument("바이올린")
                .build();

        other = User.builder()
                .id(2L)
                .email("other@test.com")
                .firebaseUid("other-uid")
                .nickname("other")
                .build();

        request = PostCreateRequestDTO.builder()
                .category("앙상블")
                .title("현악 앙상블 단원 모집")
                .eventAt(LocalDateTime.of(2024, 5, 1, 14, 0))
                .location("서울 강남구 OO스튜디오")
                .region("서울")
                .timetable("매주 토요일 오후 2시 합주")
                .description("함께 연습하고 공연할 현악 단원을 모집합니다")
                .instruments(List.of(
                        PostCreateRequestDTO.InstrumentItem.builder()
                                .instrument("바이올린").people(2).build(),
                        PostCreateRequestDTO.InstrumentItem.builder()
                                .instrument("첼로").people(1).build()
                ))
                .build();
    }

    private Post buildPost(User user) {
        Post post = Post.create(user, PostInfo.builder()
                .category(request.getCategory())
                .title(request.getTitle())
                .eventAt(request.getEventAt())
                .location(request.getLocation())
                .region(request.getRegion())
                .timetable(request.getTimetable())
                .description(request.getDescription())
                .build());
        request.getInstruments().forEach(item ->
                post.addInstrument(PostInstrument.of(post, item.getInstrument(), item.getPeople())));
        return post;
    }

    @Test
    void createPost_savesPostAndReturnsResponseWithStatusOpen() {
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostResponseDTO result = postService.createPost(owner, request);

        assertThat(result.getStatus()).isEqualTo(PostStatus.OPEN);
        assertThat(result.getTitle()).isEqualTo("현악 앙상블 단원 모집");
        assertThat(result.getCategory()).isEqualTo("앙상블");
        assertThat(result.getRegion()).isEqualTo("서울");
        assertThat(result.getDescription()).isEqualTo("함께 연습하고 공연할 현악 단원을 모집합니다");
        assertThat(result.getInstruments()).hasSize(2);
        assertThat(result.getInstruments().get(0).getInstrument()).isEqualTo("바이올린");
        assertThat(result.getInstruments().get(0).getConfirmed()).isEqualTo(0);
        assertThat(result.getInstruments().get(0).getClosed()).isFalse();
        assertThat(result.getInstruments().get(1).getInstrument()).isEqualTo("첼로");
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void createPost_publishesNewPostNotificationEventAfterSave() {
        when(postRepository.save(any(Post.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        postService.createPost(owner, request);

        ArgumentCaptor<NewPostNotificationEvent> captor =
                ArgumentCaptor.forClass(NewPostNotificationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("현악 앙상블 단원 모집");
    }

    @Test
    void getPost_returnsDetailWithApplicationCountAndFlags() {
        Post post = buildPost(owner);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(applicationService.countApplicationsByPostId(10L)).willReturn(3L);
        given(applicationService.getMyApplicationStatus(10L, other.getId()))
                .willReturn(com.wangnu.allargando.application.entity.ApplicationStatus.CANCELLED);

        PostDetailResponseDTO result = postService.getPost(10L, other);

        assertThat(result.getApplicationCount()).isEqualTo(3L);
        assertThat(result.getIsMine()).isFalse();
        assertThat(result.getHasApplied()).isTrue();
        assertThat(result.getMyApplicationStatus())
                .isEqualTo(com.wangnu.allargando.application.entity.ApplicationStatus.CANCELLED);
        assertThat(result.getWriter().getNickname()).isEqualTo("tester");
        assertThat(result.getDescription()).isEqualTo("함께 연습하고 공연할 현악 단원을 모집합니다");
    }

    @Test
    void getPost_throwsNotFoundWhenMissing() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(99L, owner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePost_updatesFieldsWhenOwner() {
        Post post = buildPost(owner);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        PostCreateRequestDTO update = PostCreateRequestDTO.builder()
                .category("앙상블")
                .title("수정된 제목")
                .eventAt(LocalDateTime.of(2024, 5, 1, 15, 0))
                .location("서울 강남구 OO스튜디오")
                .region("경기")
                .timetable("매주 토요일 오후 3시 합주")
                .description("수정된 설명")
                .instruments(List.of(
                        PostCreateRequestDTO.InstrumentItem.builder()
                                .instrument("플루트").people(1).build()
                ))
                .build();

        PostResponseDTO result = postService.updatePost(10L, owner, update);

        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getRegion()).isEqualTo("경기");
        assertThat(result.getDescription()).isEqualTo("수정된 설명");
        assertThat(result.getInstruments()).hasSize(1);
        assertThat(result.getInstruments().get(0).getInstrument()).isEqualTo("플루트");
        verify(applicationService).notifyApplicantsOfPostUpdate(10L, "수정된 제목");
    }

    // D12: 수락된 인원 밑으로 모집 인원을 줄이는 수정은 400 — 글은 바뀌지 않고 지원자 알림도 가지 않는다
    @Test
    void updatePost_throwsBadRequestWhenReducingCapacityBelowConfirmed() {
        Post post = buildPost(owner);
        post.confirmInstrument("바이올린");
        post.confirmInstrument("바이올린"); // 바이올린 2/2 확정
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        PostCreateRequestDTO update = PostCreateRequestDTO.builder()
                .category("앙상블").title("수정된 제목")
                .eventAt(LocalDateTime.of(2024, 5, 1, 15, 0))
                .location("서울 강남구 OO스튜디오").region("서울").timetable("매주 토요일")
                .instruments(List.of(
                        PostCreateRequestDTO.InstrumentItem.builder().instrument("바이올린").people(1).build(),
                        PostCreateRequestDTO.InstrumentItem.builder().instrument("첼로").people(1).build()))
                .build();

        assertThatThrownBy(() -> postService.updatePost(10L, owner, update))
                .isInstanceOf(BadRequestException.class);

        verify(applicationService, never()).notifyApplicantsOfPostUpdate(anyLong(), anyString());
        assertThat(post.getPostInstruments()).filteredOn(pi -> pi.getInstrument().equals("바이올린"))
                .singleElement().extracting(PostInstrument::getPeople).isEqualTo(2);
    }

    @Test
    void updatePost_throwsForbiddenWhenNotOwner() {
        Post post = buildPost(owner);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(10L, other, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void closePost_setsStatusClosedWhenOwner() {
        Post post = buildPost(owner);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        postService.closePost(10L, owner);

        assertThat(post.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    @Test
    void closePost_throwsForbiddenWhenNotOwner() {
        Post post = buildPost(owner);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.closePost(10L, other))
                .isInstanceOf(ForbiddenException.class);
    }

    // D11: 재개는 작성자만, 수동 마감 플래그를 풀고 상태를 다시 파생한다
    @Test
    void reopenPost_clearsManualCloseWhenOwner() {
        // buildPost의 공연일은 과거 고정값이라 재개가 거절된다 — 앞으로 열릴 공연의 글을 따로 만든다
        Post post = Post.create(owner, PostInfo.builder()
                .category("앙상블").title("다가오는 공연").location("서울").region("서울").timetable("13:00")
                .eventAt(java.time.LocalDateTime.now().plusDays(7)).build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 2));
        post.close();
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        postService.reopenPost(10L, owner);

        assertThat(post.getManuallyClosed()).isFalse();
        assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
    }

    @Test
    void reopenPost_throwsForbiddenWhenNotOwner() {
        Post post = buildPost(owner);
        post.close();
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.reopenPost(10L, other))
                .isInstanceOf(ForbiddenException.class);
        assertThat(post.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    // 공연일이 지난 글은 재개해도 의미가 없다 — 400
    @Test
    void reopenPost_throwsBadRequestWhenEventAlreadyPassed() {
        Post past = Post.create(owner, PostInfo.builder()
                .category("앙상블").title("지난 공연").location("서울").region("서울").timetable("13:00")
                .eventAt(java.time.LocalDateTime.now().minusDays(1)).build());
        past.close();
        given(postRepository.findById(10L)).willReturn(Optional.of(past));

        assertThatThrownBy(() -> postService.reopenPost(10L, owner))
                .isInstanceOf(com.wangnu.allargando.global.exception.BadRequestException.class);
        assertThat(past.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    @Test
    void deletePost_delegatesToApplicationServiceThenDeletesPostWhenOwner() {
        Post post = buildPost(owner);
        org.springframework.test.util.ReflectionTestUtils.setField(post, "id", 10L);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        postService.deletePost(10L, owner);

        org.mockito.InOrder inOrder = inOrder(applicationService, postRepository);
        inOrder.verify(applicationService).handlePostDeletion(10L, post.getTitle());
        inOrder.verify(postRepository).delete(post);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deletePost_throwsForbiddenWhenNotOwner() {
        Post post = buildPost(owner);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(10L, other))
                .isInstanceOf(ForbiddenException.class);

        verify(applicationService, never()).handlePostDeletion(any(), any());
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void getPosts_delegatesToRepositoryWithSpecification() {
        Post post = buildPost(owner);
        when(postRepository.findAll(ArgumentMatchers.<Specification<Post>>any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));

        var result = postService.getPosts(1L, null, null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("현악 앙상블 단원 모집");
    }

    @Test
    void getMyPosts_returnsSummariesForUser() {
        Post post = buildPost(owner);
        given(postRepository.findByUserId(1L, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));

        var result = postService.getMyPosts(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("현악 앙상블 단원 모집");
    }

    // 회원 탈퇴 — 작성한 모집글마다 deletePost와 같은 절차(지원서 정리 → 글 삭제)를 밟는다
    @Test
    void onUserWithdrawal_deletesEveryPostOfUserAfterApplicationCleanup() {
        Post first = mock(Post.class);
        given(first.getId()).willReturn(10L);
        given(first.getTitle()).willReturn("첫 번째 글");
        Post second = mock(Post.class);
        given(second.getId()).willReturn(11L);
        given(second.getTitle()).willReturn("두 번째 글");
        given(postRepository.findByUserId(1L)).willReturn(List.of(first, second));

        postService.onUserWithdrawal(new UserWithdrawalEvent(1L, "test-uid"));

        org.mockito.InOrder inOrder = inOrder(applicationService, postRepository);
        inOrder.verify(applicationService).handlePostDeletion(10L, "첫 번째 글");
        inOrder.verify(postRepository).delete(first);
        inOrder.verify(applicationService).handlePostDeletion(11L, "두 번째 글");
        inOrder.verify(postRepository).delete(second);
    }

    @Test
    void onUserWithdrawal_doesNothingWhenUserHasNoPosts() {
        given(postRepository.findByUserId(1L)).willReturn(List.of());

        postService.onUserWithdrawal(new UserWithdrawalEvent(1L, "test-uid"));

        verifyNoInteractions(applicationService);
        verify(postRepository, never()).delete(any(Post.class));
    }

    // D12: 글 수정으로 삭제된 악기의 대기 지원은 자동 거절을 요청한다
    @Test
    void updatePost_rejectsPendingApplicationsOfRemovedInstruments() {
        Post post = buildPost(owner);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        PostCreateRequestDTO update = PostCreateRequestDTO.builder()
                .category("앙상블").title("수정").eventAt(LocalDateTime.of(2099, 5, 1, 15, 0))
                .location("서울").region("서울").timetable("토요일")
                .instruments(List.of(
                        PostCreateRequestDTO.InstrumentItem.builder().instrument("바이올린").people(2).build()))
                .build(); // buildPost의 첼로를 뺀 요청

        postService.updatePost(10L, owner, update);

        verify(applicationService).rejectPendingByInstrument(10L, "첼로");
        verify(applicationService, never()).rejectPendingByInstrument(eq(10L), eq("바이올린"));
    }

    // 수락자가 있는 악기를 빼려는 수정은 400이고 대기 지원 거절·수정 알림도 일어나지 않는다
    @Test
    void updatePost_throwsAndTouchesNoApplicationWhenRemovingAcceptedInstrument() {
        Post post = buildPost(owner);
        post.confirmInstrument("바이올린");
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        PostCreateRequestDTO update = PostCreateRequestDTO.builder()
                .category("앙상블").title("수정").eventAt(LocalDateTime.of(2099, 5, 1, 15, 0))
                .location("서울").region("서울").timetable("토요일")
                .instruments(List.of(
                        PostCreateRequestDTO.InstrumentItem.builder().instrument("첼로").people(1).build()))
                .build();

        assertThatThrownBy(() -> postService.updatePost(10L, owner, update))
                .isInstanceOf(BadRequestException.class);

        verify(applicationService, never()).rejectPendingByInstrument(anyLong(), anyString());
        verify(applicationService, never()).notifyApplicantsOfPostUpdate(anyLong(), anyString());
    }
}
