package com.wangnu.allargando.practice.service;

import com.wangnu.allargando.user.event.UserWithdrawalEvent;
import com.wangnu.allargando.global.exception.ForbiddenException;
import com.wangnu.allargando.global.exception.NotFoundException;
import com.wangnu.allargando.practice.dto.PracticeLogCreateRequestDTO;
import com.wangnu.allargando.practice.dto.PracticeLogResponseDTO;
import com.wangnu.allargando.practice.dto.PracticeLogSummaryResponseDTO;
import com.wangnu.allargando.practice.entity.PracticeLog;
import com.wangnu.allargando.practice.repository.PracticeLogRepository;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticeLogServiceTest {

    @Mock private PracticeLogRepository practiceLogRepository;
    @InjectMocks private PracticeLogService practiceLogService;

    private User owner;
    private User other;
    private PracticeLogCreateRequestDTO request;

    @BeforeEach
    void setUp() {
        owner = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now())
                .id(1L)
                .firebaseUid("test-uid")
                .phoneNumber("010-1234-5678")
                .nickname("tester")
                .instrument("바이올린")
                .build();

        other = User.builder().name("테스터").termsAgreedAt(java.time.LocalDateTime.now())
                .id(2L)
                .firebaseUid("other-uid")
                .nickname("other")
                .build();

        request = PracticeLogCreateRequestDTO.builder()
                .title("Bach 무반주 파르티타 1번 연습")
                .logDate(LocalDate.of(2026, 7, 4))
                .duration(90)
                .content("알레망드 첫 번째 섹션 느린 템포로 반복")
                .build();
    }

    private PracticeLog buildLog(User user) {
        return PracticeLog.create(user, request.getTitle(), request.getLogDate(),
                request.getDuration(), request.getContent());
    }

    @Test
    void createPracticeLog_savesLogAndReturnsResponse() {
        when(practiceLogRepository.save(any(PracticeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PracticeLogResponseDTO result = practiceLogService.createPracticeLog(owner, request);

        assertThat(result.getTitle()).isEqualTo("Bach 무반주 파르티타 1번 연습");
        assertThat(result.getLogDate()).isEqualTo(LocalDate.of(2026, 7, 4));
        assertThat(result.getDuration()).isEqualTo(90);
        assertThat(result.getContent()).isEqualTo("알레망드 첫 번째 섹션 느린 템포로 반복");
        verify(practiceLogRepository, times(1)).save(any(PracticeLog.class));
    }

    @Test
    void getMyPracticeLogs_returnsSummariesForUser() {
        PracticeLog log = buildLog(owner);
        given(practiceLogRepository.findByUserId(1L, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1));

        Page<PracticeLogSummaryResponseDTO> result =
                practiceLogService.getMyPracticeLogs(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Bach 무반주 파르티타 1번 연습");
    }

    @Test
    void getPracticeLog_returnsDetailWhenOwner() {
        PracticeLog log = buildLog(owner);
        given(practiceLogRepository.findById(10L)).willReturn(Optional.of(log));

        PracticeLogResponseDTO result = practiceLogService.getPracticeLog(10L, owner);

        assertThat(result.getTitle()).isEqualTo("Bach 무반주 파르티타 1번 연습");
        assertThat(result.getContent()).isEqualTo("알레망드 첫 번째 섹션 느린 템포로 반복");
    }

    @Test
    void getPracticeLog_throwsNotFoundWhenMissing() {
        given(practiceLogRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> practiceLogService.getPracticeLog(99L, owner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPracticeLog_throwsForbiddenWhenNotOwner() {
        PracticeLog log = buildLog(owner);
        given(practiceLogRepository.findById(10L)).willReturn(Optional.of(log));

        assertThatThrownBy(() -> practiceLogService.getPracticeLog(10L, other))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updatePracticeLog_updatesFieldsWhenOwner() {
        PracticeLog log = buildLog(owner);
        given(practiceLogRepository.findById(10L)).willReturn(Optional.of(log));

        PracticeLogCreateRequestDTO update = PracticeLogCreateRequestDTO.builder()
                .title("수정된 제목")
                .logDate(LocalDate.of(2026, 7, 5))
                .duration(60)
                .content("수정된 내용")
                .build();

        PracticeLogResponseDTO result = practiceLogService.updatePracticeLog(10L, owner, update);

        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getLogDate()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(result.getDuration()).isEqualTo(60);
        assertThat(result.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    void updatePracticeLog_throwsForbiddenWhenNotOwner() {
        PracticeLog log = buildLog(owner);
        given(practiceLogRepository.findById(10L)).willReturn(Optional.of(log));

        assertThatThrownBy(() -> practiceLogService.updatePracticeLog(10L, other, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletePracticeLog_deletesLogWhenOwner() {
        PracticeLog log = buildLog(owner);
        given(practiceLogRepository.findById(10L)).willReturn(Optional.of(log));

        practiceLogService.deletePracticeLog(10L, owner);

        verify(practiceLogRepository, times(1)).delete(log);
    }

    @Test
    void deletePracticeLog_throwsForbiddenWhenNotOwner() {
        PracticeLog log = buildLog(owner);
        given(practiceLogRepository.findById(10L)).willReturn(Optional.of(log));

        assertThatThrownBy(() -> practiceLogService.deletePracticeLog(10L, other))
                .isInstanceOf(ForbiddenException.class);

        verify(practiceLogRepository, never()).delete(any(PracticeLog.class));
    }

    // 회원 탈퇴 — 이 유저의 연습 일지를 전부 삭제
    @Test
    void onUserWithdrawal_deletesAllLogsOfUser() {
        practiceLogService.onUserWithdrawal(new UserWithdrawalEvent(1L, "test-uid"));

        verify(practiceLogRepository).deleteByUserId(1L);
    }
}
