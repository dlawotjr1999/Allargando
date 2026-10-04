package com.wangnu.allargando.block.service;

import com.wangnu.allargando.block.dto.BlockedUserResponseDTO;
import com.wangnu.allargando.block.entity.UserBlock;
import com.wangnu.allargando.block.repository.UserBlockRepository;
import com.wangnu.allargando.global.exception.BadRequestException;
import com.wangnu.allargando.global.exception.ConflictGuard;
import com.wangnu.allargando.global.exception.NotFoundException;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.user.event.UserWithdrawalEvent;
import com.wangnu.allargando.user.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/*
 * 유저 차단 비즈니스 로직
 * 차단·해제·내 차단 목록 조회. 차단의 효과(목록 숨김·지원 막기)는 이 서비스가 아니라
 * 쓰는 쪽이 정한다 — 모집글 목록은 PostSpecification, 지원 제한은 ApplicationService가 isBlocked로 확인
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserService userService;

    // 유저 차단 — 본인은 불가, 이미 차단한 유저는 409
    @Transactional
    public void block(User user, String nickname) {
        User blocker = userService.getManagedUserById(user.getId());
        User blocked = userService.getManagedUserByNickname(nickname);

        if (blocker.getId().equals(blocked.getId())) {
            throw new BadRequestException("본인을 차단할 수 없습니다");
        }
        ConflictGuard.requireUnique(
                userBlockRepository.existsByBlockerIdAndBlockedId(blocker.getId(), blocked.getId()),
                "이미 차단한 사용자입니다");

        userBlockRepository.save(UserBlock.of(blocker, blocked));
    }

    // 차단 해제 — 차단 내역이 없으면 404
    @Transactional
    public void unblock(User user, String nickname) {
        User blocked = userService.getManagedUserByNickname(nickname);
        UserBlock block = userBlockRepository.findByBlockerIdAndBlockedId(user.getId(), blocked.getId())
                .orElseThrow(() -> new NotFoundException("차단한 사용자가 아닙니다"));
        userBlockRepository.delete(block);
    }

    // 내 차단 목록 (최근 차단 순)
    @Transactional(readOnly = true)
    public List<BlockedUserResponseDTO> getMyBlocks(Long userId) {
        return userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(userId).stream()
                .map(BlockedUserResponseDTO::from)
                .collect(Collectors.toList());
    }

    // blockerId가 blockedId를 차단했는지 — 다른 도메인(지원 제한 등)이 차단 여부만 물을 때 쓰는 진입점
    @Transactional(readOnly = true)
    public boolean isBlocked(Long blockerId, Long blockedId) {
        return userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    // 회원 탈퇴 시 이 유저가 얽힌 차단 기록(차단한 것·당한 것) 전부 삭제 — UserService가 발행한
    // UserWithdrawalEvent를 같은 트랜잭션에서 처리(유저 행 삭제보다 먼저 실행돼야 FK 위반이 없다, CLAUDE.md §3.8)
    @EventListener
    @Transactional
    public void onUserWithdrawal(UserWithdrawalEvent event) {
        userBlockRepository.deleteAllInvolving(event.userId());
    }
}
