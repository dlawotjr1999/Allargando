package com.obri_back.obri.practice.service;

import com.obri_back.obri.global.exception.ForbiddenException;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.practice.dto.PracticeLogCreateRequestDTO;
import com.obri_back.obri.practice.dto.PracticeLogResponseDTO;
import com.obri_back.obri.practice.dto.PracticeLogSummaryResponseDTO;
import com.obri_back.obri.practice.entity.PracticeLog;
import com.obri_back.obri.practice.repository.PracticeLogRepository;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * 연습 일지 관련 비즈니스 로직
 * 등록·조회(내 목록/단건)·수정·삭제. 항상 작성자 본인만 접근 가능(공개 목록 없음)
 */
@Service
@RequiredArgsConstructor
public class PracticeLogService {

    private final PracticeLogRepository practiceLogRepository;

    // 연습 일지 등록
    @Transactional
    public PracticeLogResponseDTO createPracticeLog(User user, PracticeLogCreateRequestDTO request) {
        PracticeLog log = PracticeLog.create(user, request.getTitle(), request.getLogDate(),
                request.getDuration(), request.getContent());
        PracticeLog saved = practiceLogRepository.save(log);
        return PracticeLogResponseDTO.from(saved);
    }

    // 내 연습 일지 목록 조회 — 카드 리스트용 요약. 정렬은 컨트롤러 Pageable에서 결정
    @Transactional(readOnly = true)
    public Page<PracticeLogSummaryResponseDTO> getMyPracticeLogs(Long userId, Pageable pageable) {
        return practiceLogRepository.findByUserId(userId, pageable).map(PracticeLogSummaryResponseDTO::from);
    }

    // 연습 일지 단건 조회 (작성자 본인만)
    @Transactional(readOnly = true)
    public PracticeLogResponseDTO getPracticeLog(Long logId, User user) {
        PracticeLog log = findLogOrThrow(logId);
        requireOwner(log, user);
        return PracticeLogResponseDTO.from(log);
    }

    // 연습 일지 수정 (작성자 본인만) — 전체 필드 교체
    @Transactional
    public PracticeLogResponseDTO updatePracticeLog(Long logId, User user, PracticeLogCreateRequestDTO request) {
        PracticeLog log = findLogOrThrow(logId);
        requireOwner(log, user);

        log.update(request.getTitle(), request.getLogDate(), request.getDuration(), request.getContent());

        return PracticeLogResponseDTO.from(log);
    }

    // 연습 일지 삭제 (작성자 본인만)
    @Transactional
    public void deletePracticeLog(Long logId, User user) {
        PracticeLog log = findLogOrThrow(logId);
        requireOwner(log, user);
        practiceLogRepository.delete(log);
    }

    // 회원 탈퇴 시 이 유저의 연습 일지 전부 삭제 — UserService가 발행한 UserWithdrawalEvent를 같은 트랜잭션에서 처리
    // (유저 행 삭제보다 먼저 실행돼야 FK 위반이 없다)
    @EventListener
    @Transactional
    public void onUserWithdrawal(UserWithdrawalEvent event) {
        practiceLogRepository.deleteByUserId(event.userId());
    }

    // 연습 일지 조회 공통 헬퍼 — 없으면 404
    private PracticeLog findLogOrThrow(Long logId) {
        return practiceLogRepository.findById(logId)
                .orElseThrow(() -> new NotFoundException("연습 일지를 찾을 수 없습니다"));
    }

    // 작성자 본인 여부 검증 — 아니면 403
    private void requireOwner(PracticeLog log, User user) {
        if (!log.isOwnedBy(user)) {
            throw new ForbiddenException("작성자만 처리할 수 있습니다");
        }
    }
}
