package com.obri_back.obri.block.service;

import com.obri_back.obri.block.dto.BlockedUserResponseDTO;
import com.obri_back.obri.block.entity.UserBlock;
import com.obri_back.obri.block.repository.UserBlockRepository;
import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.global.exception.ConflictException;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.user.entity.User;
import com.obri_back.obri.user.event.UserWithdrawalEvent;
import com.obri_back.obri.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock UserBlockRepository userBlockRepository;
    @Mock UserService userService;
    @InjectMocks BlockService blockService;

    private User me;
    private User target;

    @BeforeEach
    void setUp() {
        me = User.builder().id(1L).nickname("me").instrument("바이올린").build();
        target = User.builder().id(2L).nickname("target").instrument("첼로").build();
    }

    @Test
    void block_savesBlockFromMeToTarget() {
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(userService.getManagedUserByNickname("target")).willReturn(target);
        given(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).willReturn(false);

        blockService.block(me, "target");

        ArgumentCaptor<UserBlock> captor = ArgumentCaptor.forClass(UserBlock.class);
        verify(userBlockRepository).save(captor.capture());
        assertThat(captor.getValue().getBlocker()).isEqualTo(me);
        assertThat(captor.getValue().getBlocked()).isEqualTo(target);
    }

    @Test
    void block_throwsBadRequestWhenBlockingSelf() {
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(userService.getManagedUserByNickname("me")).willReturn(me);

        assertThatThrownBy(() -> blockService.block(me, "me"))
                .isInstanceOf(BadRequestException.class);

        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void block_throwsConflictWhenAlreadyBlocked() {
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(userService.getManagedUserByNickname("target")).willReturn(target);
        given(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).willReturn(true);

        assertThatThrownBy(() -> blockService.block(me, "target"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 차단한 사용자입니다");

        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void block_throwsNotFoundWhenNicknameUnknown() {
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(userService.getManagedUserByNickname("ghost")).willThrow(new NotFoundException("유저를 찾을 수 없습니다"));

        assertThatThrownBy(() -> blockService.block(me, "ghost"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unblock_deletesExistingBlock() {
        UserBlock block = UserBlock.of(me, target);
        given(userService.getManagedUserByNickname("target")).willReturn(target);
        given(userBlockRepository.findByBlockerIdAndBlockedId(1L, 2L)).willReturn(Optional.of(block));

        blockService.unblock(me, "target");

        verify(userBlockRepository).delete(block);
    }

    @Test
    void unblock_throwsNotFoundWhenNotBlocked() {
        given(userService.getManagedUserByNickname("target")).willReturn(target);
        given(userBlockRepository.findByBlockerIdAndBlockedId(1L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.unblock(me, "target"))
                .isInstanceOf(NotFoundException.class);

        verify(userBlockRepository, never()).delete(any(UserBlock.class));
    }

    @Test
    void getMyBlocks_mapsBlockedUsersToResponse() {
        given(userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(UserBlock.of(me, target)));

        List<BlockedUserResponseDTO> result = blockService.getMyBlocks(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNickname()).isEqualTo("target");
        assertThat(result.get(0).getInstrument()).isEqualTo("첼로");
    }

    @Test
    void isBlocked_delegatesToRepository() {
        given(userBlockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).willReturn(true);

        assertThat(blockService.isBlocked(2L, 1L)).isTrue();
        assertThat(blockService.isBlocked(1L, 2L)).isFalse();
    }

    @Test
    void onUserWithdrawal_deletesEveryBlockInvolvingUser() {
        blockService.onUserWithdrawal(new UserWithdrawalEvent(1L, "me-uid"));

        verify(userBlockRepository).deleteAllInvolving(1L);
    }
}
