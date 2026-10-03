package com.obri_back.obri.report.service;

import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.global.exception.ConflictException;
import com.obri_back.obri.global.exception.NotFoundException;
import com.obri_back.obri.post.entity.Post;
import com.obri_back.obri.post.repository.PostRepository;
import com.obri_back.obri.report.dto.ReportRequestDTO;
import com.obri_back.obri.report.entity.Report;
import com.obri_back.obri.report.entity.ReportReason;
import com.obri_back.obri.report.entity.ReportStatus;
import com.obri_back.obri.report.entity.ReportTargetType;
import com.obri_back.obri.report.repository.ReportRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock PostRepository postRepository;
    @Mock UserService userService;
    @InjectMocks ReportService reportService;

    private User me;
    private User other;
    private ReportRequestDTO request;

    @BeforeEach
    void setUp() {
        me = User.builder().id(1L).nickname("me").build();
        other = User.builder().id(2L).nickname("other").build();
        request = ReportRequestDTO.builder().reason(ReportReason.SPAM).detail("광고글입니다").build();
    }

    private Post postOwnedBy(User owner) {
        Post post = mock(Post.class);
        // 본인 글 케이스는 id를 읽기 전에 예외가 나므로 lenient
        lenient().when(post.getId()).thenReturn(10L);
        given(post.isOwnedBy(me)).willReturn(owner.getId().equals(me.getId()));
        return post;
    }

    @Test
    void reportPost_savesPendingReportWhenValid() {
        Post othersPost = postOwnedBy(other);
        given(postRepository.findById(10L)).willReturn(Optional.of(othersPost));
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 10L))
                .willReturn(false);
        given(reportRepository.save(any(Report.class))).willAnswer(inv -> inv.getArgument(0));

        reportService.reportPost(me, 10L, request);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getReporter()).isEqualTo(me);
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.POST);
        assertThat(saved.getTargetId()).isEqualTo(10L);
        assertThat(saved.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(saved.getDetail()).isEqualTo("광고글입니다");
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void reportPost_throwsNotFoundWhenPostMissing() {
        given(postRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.reportPost(me, 10L, request))
                .isInstanceOf(NotFoundException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportPost_throwsBadRequestWhenReportingOwnPost() {
        Post myPost = postOwnedBy(me);
        given(postRepository.findById(10L)).willReturn(Optional.of(myPost));

        assertThatThrownBy(() -> reportService.reportPost(me, 10L, request))
                .isInstanceOf(BadRequestException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportPost_throwsConflictWhenAlreadyReported() {
        Post othersPost = postOwnedBy(other);
        given(postRepository.findById(10L)).willReturn(Optional.of(othersPost));
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 10L))
                .willReturn(true);

        assertThatThrownBy(() -> reportService.reportPost(me, 10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 신고한 모집글입니다");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportUser_savesReportAgainstUserId() {
        given(userService.getManagedUserByNickname("other")).willReturn(other);
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.USER, 2L))
                .willReturn(false);
        given(reportRepository.save(any(Report.class))).willAnswer(inv -> inv.getArgument(0));

        reportService.reportUser(me, "other", request);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo(ReportTargetType.USER);
        assertThat(captor.getValue().getTargetId()).isEqualTo(2L);
    }

    @Test
    void reportUser_throwsBadRequestWhenReportingSelf() {
        given(userService.getManagedUserByNickname("me")).willReturn(me);

        assertThatThrownBy(() -> reportService.reportUser(me, "me", request))
                .isInstanceOf(BadRequestException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportUser_throwsConflictWhenAlreadyReported() {
        given(userService.getManagedUserByNickname("other")).willReturn(other);
        given(userService.getManagedUserById(1L)).willReturn(me);
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.USER, 2L))
                .willReturn(true);

        assertThatThrownBy(() -> reportService.reportUser(me, "other", request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 신고한 사용자입니다");
    }

    @Test
    void onUserWithdrawal_deletesReportsMadeByAndAgainstUser() {
        reportService.onUserWithdrawal(new UserWithdrawalEvent(1L, "me-uid"));

        verify(reportRepository).deleteAllInvolving(1L, ReportTargetType.USER, ReportTargetType.POST);
    }
}
