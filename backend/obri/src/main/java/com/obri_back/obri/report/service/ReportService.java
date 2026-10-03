package com.obri_back.obri.report.service;

import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.global.exception.ConflictGuard;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.post.entity.Post;
import com.obri_back.obri.post.repository.PostRepository;
import com.obri_back.obri.report.dto.ReportRequestDTO;
import com.obri_back.obri.report.entity.Report;
import com.obri_back.obri.report.entity.ReportTargetType;
import com.obri_back.obri.report.repository.ReportRepository;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.obri_back.obri.user.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * 신고 비즈니스 로직
 * 모집글·유저 신고를 접수해 저장한다. 관리자 화면이 없으므로 운영자는 서버 로그의 "[신고 접수]" 줄이나
 * report 테이블(status=PENDING)을 직접 조회해 확인하고, 조치 후 status를 RESOLVED로 바꾼다(docs/RELEASE_PLAN.md 운영 절차)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    // 모집글 존재·작성자 확인은 ApplicationService와 같이 PostRepository를 직접 사용(읽기 전용 확인이라 서비스 경유 불필요)
    private final PostRepository postRepository;
    private final UserService userService;

    // 모집글 신고 — 없는 글은 404, 본인 글은 400, 이미 신고한 글은 409
    @Transactional
    public void reportPost(User user, Long postId, ReportRequestDTO request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("모집글을 찾을 수 없습니다"));
        if (post.isOwnedBy(user)) {
            throw new BadRequestException("본인 모집글은 신고할 수 없습니다");
        }
        save(user, ReportTargetType.POST, post.getId(), request, "이미 신고한 모집글입니다");
    }

    // 유저 신고 — 없는 유저는 404, 본인은 400, 이미 신고한 유저는 409
    @Transactional
    public void reportUser(User user, String nickname, ReportRequestDTO request) {
        User target = userService.getManagedUserByNickname(nickname);
        if (target.getId().equals(user.getId())) {
            throw new BadRequestException("본인을 신고할 수 없습니다");
        }
        save(user, ReportTargetType.USER, target.getId(), request, "이미 신고한 사용자입니다");
    }

    // 중복 확인 후 신고 저장 + 운영자가 로그로 알 수 있게 접수 기록.
    // 설명(detail)은 로그에 남기지 않는다 — 신고자가 임의로 적은 개인정보가 섞일 수 있다
    private void save(User user, ReportTargetType targetType, Long targetId,
                      ReportRequestDTO request, String duplicateMessage) {
        User reporter = userService.getManagedUserById(user.getId());
        ConflictGuard.requireUnique(
                reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporter.getId(), targetType, targetId),
                duplicateMessage);

        Report report = reportRepository.save(
                Report.create(reporter, targetType, targetId, request.getReason(), request.getDetail()));
        log.warn("[신고 접수] reportId={} target={}#{} reason={} reporterId={}",
                report.getId(), targetType, targetId, request.getReason(), reporter.getId());
    }

    // 회원 탈퇴 시 이 유저가 한 신고·이 유저를 대상으로 한 신고 삭제 — UserService가 발행한 UserWithdrawalEvent를
    // 같은 트랜잭션에서 처리(유저 행 삭제보다 먼저 실행돼야 FK 위반이 없다, CLAUDE.md §3.8)
    @EventListener
    @Transactional
    public void onUserWithdrawal(UserWithdrawalEvent event) {
        reportRepository.deleteAllInvolving(event.userId(), ReportTargetType.USER);
    }
}
