package com.wangnu.allargando.post.service;

import com.wangnu.allargando.application.entity.ApplicationStatus;
import com.wangnu.allargando.application.service.ApplicationService;
import com.wangnu.allargando.global.exception.BadRequestException;
import com.wangnu.allargando.global.exception.ForbiddenException;
import com.wangnu.allargando.global.exception.NotFoundException;
import com.wangnu.allargando.notification.event.NewPostNotificationEvent;
import com.wangnu.allargando.post.dto.PostCreateRequestDTO;
import com.wangnu.allargando.post.dto.PostDetailResponseDTO;
import com.wangnu.allargando.post.dto.PostResponseDTO;
import com.wangnu.allargando.post.dto.PostSummaryResponseDTO;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.post.entity.PostStatus;
import com.wangnu.allargando.post.repository.PostRepository;
import com.wangnu.allargando.post.repository.PostSort;
import com.wangnu.allargando.post.repository.PostSpecification;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.event.UserWithdrawalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/*
 * 모집글 관련 비즈니스 로직
 * 등록·조회(전체/내글/단건)·수정·마감·삭제 및 악기 확정 상태 관리
 * 상태 변경·알림 발송을 각 도메인/서비스에 위임
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    // 읽기(applicationCount·hasApplied)는 CLAUDE.md §2가 문서화한 예외로 직접 조회
    // 쓰기(수정·삭제 시 지원자 처리)는 notifyApplicantsOfPostUpdate·handlePostDeletion에 위임 — BACKLOG.md #12
    private final ApplicationService applicationService;
    private final ApplicationEventPublisher eventPublisher;

    // 모집글 등록 — 악기 목록을 함께 저장하고 전체 broadcast 알림 발송
    @Transactional
    public PostResponseDTO createPost(User user, PostCreateRequestDTO request) {
        Post post = Post.create(user, toPostInfo(request));
        request.getInstruments().forEach(item ->
                post.addInstrument(PostInstrument.of(post, item.getInstrument(), item.getPeople()))
        );
        Post saved = postRepository.save(post);
        // 새 모집글 → 전체 구독자에게 broadcast. AFTER_COMMIT 이후 발송(BACKLOG.md #33) — 이 트랜잭션이
        // 롤백되면 이벤트 자체가 버려져 존재하지 않는 모집글의 알림이 나가지 않는다
        eventPublisher.publishEvent(new NewPostNotificationEvent(saved.getId(), saved.getTitle()));
        return PostResponseDTO.from(saved);
    }

    // 모집글 전체 조회 — Specification 동적 필터·정렬 적용 후 요약 DTO로 반환
    // statuses가 비면 모집 중인 글(OPEN·PARTIALLY_CLOSED)만, CLOSED를 고르면 마감된 글도 보인다(D15)
    // sort(LATEST·EVENT_SOON·CLOSING_SOON)는 Specification이 쿼리에 직접 건다 — pageable은 정렬이 없어야 한다
    // viewerId(조회하는 유저)가 차단한 작성자의 글은 목록에서 제외된다
    @Transactional(readOnly = true)
    public Page<PostSummaryResponseDTO> getPosts(Long viewerId, List<String> categories, List<String> instruments,
            List<String> regions, LocalDate startDate, LocalDate endDate, List<PostStatus> statuses, PostSort sort,
            Pageable pageable) {
        Specification<Post> spec = PostSpecification.filter(
                viewerId, categories, instruments, regions, startDate, endDate, statuses, sort);
        return postRepository.findAll(spec, pageable).map(PostSummaryResponseDTO::from);
    }

    // 내가 올린 모집글 목록(마이페이지) — 카드 리스트용 요약. 정렬은 컨트롤러 Pageable에서 결정
    @Transactional(readOnly = true)
    public Page<PostSummaryResponseDTO> getMyPosts(Long userId, Pageable pageable) {
        return postRepository.findByUserId(userId, pageable).map(PostSummaryResponseDTO::from);
    }

    // 모집글 단건 조회 — applicationCount·isMine·내 지원 상태(myApplicationStatus, hasApplied)를 계산해 상세 DTO 반환
    @Transactional(readOnly = true)
    public PostDetailResponseDTO getPost(Long postId, User user) {
        Post post = findPostOrThrow(postId);

        long applicationCount = applicationService.countApplicationsByPostId(postId);
        boolean isMine = post.isOwnedBy(user);
        ApplicationStatus myApplicationStatus = applicationService.getMyApplicationStatus(postId, user.getId());

        return PostDetailResponseDTO.from(post, applicationCount, isMine, myApplicationStatus);
    }

    // 모집글 수정 (작성자만) — 악기 목록 전체 교체. 지원자에게 보일 내용이 실제로 바뀐 경우에만 대기·수락 지원자에게 알림
    @Transactional
    public PostResponseDTO updatePost(Long postId, User user, PostCreateRequestDTO request) {
        Post post = findPostOrThrow(postId);
        requireOwner(post, user);

        PostInfo info = toPostInfo(request);
        List<PostInstrument> newInstruments = request.getInstruments().stream()
                .map(item -> PostInstrument.of(post, item.getInstrument(), item.getPeople()))
                .collect(Collectors.toList());
        // 변경 여부는 적용 전 값과 비교해야 하므로 updateInfo·replaceInstruments보다 먼저 계산한다
        boolean changed = post.hasApplicantVisibleChange(info, newInstruments);

        post.updateInfo(info);
        List<String> removedInstruments = post.replaceInstruments(newInstruments);
        // 삭제된 악기로 들어와 있던 대기 지원은 자동 거절하고 알린다(D12 — 수락자가 있는 악기는 위에서 이미 400)
        removedInstruments.forEach(name -> applicationService.rejectPendingByInstrument(postId, name));

        // 모집글 수정 → 실제 변경이 있을 때만 알린다. 누구에게 보낼지는 Application 도메인이 결정 — 명세 시나리오 1.8
        if (changed) {
            applicationService.notifyApplicantsOfPostUpdate(postId, post.getTitle());
        }

        return PostResponseDTO.from(post);
    }

    // 모집글 수동 전체 마감 (작성자만)
    @Transactional
    public void closePost(Long postId, User user) {
        Post post = findPostOrThrow(postId);
        requireOwner(post, user);
        post.close();
    }

    // 모집글 수동 마감 해제(재개, 작성자만) — 공연일이 지난 글은 재개할 수 없다(400). 정원이 모두 찬 글은 재개해도 CLOSED.
    // 마감 때문에 자동 거절된 지원자는 복구하지 않고, 수동 마감·재개에는 알림이 없다(D10·D17)
    @Transactional
    public void reopenPost(Long postId, User user) {
        Post post = findPostOrThrow(postId);
        requireOwner(post, user);
        if (post.getEventAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("공연일이 지난 모집글은 모집을 재개할 수 없습니다");
        }
        post.reopen();
    }

    // 모집글 삭제 (작성자만) — 지원서 정리·삭제 알림은 Application 도메인에 위임
    @Transactional
    public void deletePost(Long postId, User user) {
        Post post = findPostOrThrow(postId);
        requireOwner(post, user);

        removePost(post);
    }

    // 회원 탈퇴 시 이 유저가 작성한 모집글 전부 삭제 — UserService가 발행한 UserWithdrawalEvent를 같은 트랜잭션에서
    // 처리한다(유저 행 삭제보다 먼저 실행돼야 FK 위반이 없다). 글마다 deletePost와 같은 절차로 지원서 정리·삭제 알림까지 한다.
    @EventListener
    @Transactional
    public void onUserWithdrawal(UserWithdrawalEvent event) {
        postRepository.findByUserId(event.userId()).forEach(this::removePost);
    }

    // 모집글 삭제 절차 — 지원서 정리·삭제 알림(Application 도메인에 위임)을 먼저 하고 Post를 지운다(FK 순서 보장)
    private void removePost(Post post) {
        applicationService.handlePostDeletion(post.getId(), post.getTitle());
        postRepository.delete(post);
    }

    // PostCreateRequestDTO → PostInfo 변환 (엔티티가 웹 DTO를 직접 받지 않도록 분리)
    private PostInfo toPostInfo(PostCreateRequestDTO request) {
        return PostInfo.builder()
                .category(request.getCategory())
                .title(request.getTitle())
                .eventAt(request.getEventAt())
                .location(request.getLocation())
                .region(request.getRegion())
                .timetable(request.getTimetable())
                .description(request.getDescription())
                .build();
    }

    // 모집글 조회 공통 헬퍼 — 없으면 404
    private Post findPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("모집글을 찾을 수 없습니다"));
    }

    // 작성자 본인 여부 검증 — 아니면 403
    private void requireOwner(Post post, User user) {
        if (!post.isOwnedBy(user)) {
            throw new ForbiddenException("작성자만 처리할 수 있습니다");
        }
    }
}
