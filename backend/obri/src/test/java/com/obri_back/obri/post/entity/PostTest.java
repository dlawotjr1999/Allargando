package com.obri_back.obri.post.entity;

import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 악기별 confirmed/closed에 따른 글 전체 상태(OPEN/PARTIALLY_CLOSED/CLOSED) 파생 로직 검증
class PostTest {

    private Post post; // 바이올린 people=2, 첼로 people=1

    @BeforeEach
    void setUp() {
        User owner = User.builder().id(1L).nickname("owner").firebaseUid("owner-uid").build();

        post = Post.create(owner, PostInfo.builder().build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 2));
        post.addInstrument(PostInstrument.of(post, "첼로", 1));
    }

    private PostInstrument instrument(String name) {
        return post.getPostInstruments().stream()
                .filter(pi -> pi.getInstrument().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void confirmInstrument_partialClose_setsPartiallyClosed() {
        post.confirmInstrument("첼로"); // people=1 → 즉시 마감

        assertThat(instrument("첼로").getClosed()).isTrue();
        assertThat(instrument("첼로").getConfirmed()).isEqualTo(1);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PARTIALLY_CLOSED);
    }

    @Test
    void confirmInstrument_partialFill_keepsOpenUntilCapacity() {
        post.confirmInstrument("바이올린"); // people=2, confirmed=1 → 아직 미마감

        assertThat(instrument("바이올린").getClosed()).isFalse();
        assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
    }

    @Test
    void confirmInstrument_allClosed_setsClosed() {
        post.confirmInstrument("첼로");
        post.confirmInstrument("바이올린");
        post.confirmInstrument("바이올린"); // 바이올린 정원 도달

        assertThat(post.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    @Test
    void revokeInstrument_reopensFromClosed() {
        post.confirmInstrument("첼로");
        post.confirmInstrument("바이올린");
        post.confirmInstrument("바이올린"); // CLOSED

        post.revokeInstrument("바이올린"); // 바이올린 재오픈, 첼로는 여전히 마감

        assertThat(instrument("바이올린").getClosed()).isFalse();
        assertThat(instrument("바이올린").getConfirmed()).isEqualTo(1);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PARTIALLY_CLOSED);
    }

    @Test
    void confirmInstrument_throwsWhenAlreadyClosed() {
        post.confirmInstrument("첼로"); // 마감됨

        assertThatThrownBy(() -> post.confirmInstrument("첼로"))
                .isInstanceOf(BadRequestException.class);
    }

    // 시나리오 1.4: 지원자 전공이 모집 목록에 없으면 수락 허용(자리 미반영, 상태만 변경) — 예외 아님
    @Test
    void confirmInstrument_nonRecruitedInstrument_keepsStatusAndSeats() {
        post.confirmInstrument("트럼펫"); // 모집 목록에 없는 악기 → 무동작

        assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
        assertThat(instrument("바이올린").getConfirmed()).isEqualTo(0);
        assertThat(instrument("첼로").getConfirmed()).isEqualTo(0);
    }

    // 미반영 수락(모집 목록에 없는 악기)의 철회는 되돌릴 자리가 없으므로 무동작
    @Test
    void revokeInstrument_nonRecruitedInstrument_noOp() {
        post.revokeInstrument("트럼펫");

        assertThat(post.getStatus()).isEqualTo(PostStatus.OPEN);
        assertThat(instrument("바이올린").getConfirmed()).isEqualTo(0);
    }

    // BACKLOG.md #23: 이미 정원 마감된 악기는 지원 시점에 사전 차단하기 위한 조회용 메서드
    @Test
    void isInstrumentClosed_trueWhenInstrumentAlreadyClosed() {
        post.confirmInstrument("첼로"); // people=1 → 즉시 마감

        assertThat(post.isInstrumentClosed("첼로")).isTrue();
    }

    @Test
    void isInstrumentClosed_falseWhenInstrumentStillOpen() {
        post.confirmInstrument("바이올린"); // people=2, confirmed=1 → 아직 미마감

        assertThat(post.isInstrumentClosed("바이올린")).isFalse();
    }

    // 모집 목록에 없는 악기는 마감 개념이 없으므로 false(자리 미반영 지원 허용, 시나리오 1.4와 일관)
    @Test
    void isInstrumentClosed_falseWhenInstrumentNotRecruited() {
        assertThat(post.isInstrumentClosed("트럼펫")).isFalse();
    }

    // BACKLOG.md #31: 수동 마감은 악기 상태 파생과 별개로 유지되어야 함
    @Test
    void close_thenRevokeInstrument_staysClosedInsteadOfReopening() {
        post.confirmInstrument("첼로"); // people=1 → 첼로 즉시 마감
        post.close(); // 작성자가 나머지(바이올린)까지 포함해 수동 전체 마감

        post.revokeInstrument("첼로"); // 지원 수락 철회로 첼로 재오픈 시도

        assertThat(post.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    @Test
    void close_setsManuallyClosedFlag() {
        post.close();

        assertThat(post.getManuallyClosed()).isTrue();
    }

    // BACKLOG.md #32: 글 수정 시 이름이 같은 악기는 확정 인원·마감 상태를 승계해야 함
    @Test
    void replaceInstruments_matchingNamePreservesConfirmedCount() {
        post.confirmInstrument("첼로"); // confirmed=1, people=1 → closed=true

        post.replaceInstruments(List.of(
                PostInstrument.of(post, "첼로", 1),
                PostInstrument.of(post, "바이올린", 2)
        ));

        assertThat(instrument("첼로").getConfirmed()).isEqualTo(1);
        assertThat(instrument("첼로").getClosed()).isTrue();
    }

    @Test
    void replaceInstruments_removesInstrumentNotInNewList() {
        post.replaceInstruments(List.of(PostInstrument.of(post, "바이올린", 2)));

        assertThat(post.getPostInstruments()).hasSize(1);
        assertThat(post.getPostInstruments().get(0).getInstrument()).isEqualTo("바이올린");
    }

    // 악기명 중복 등록 검증이 없던 시절 유입된 레거시 데이터(같은 이름 중복)에서도
    // replaceInstruments가 Duplicate key IllegalStateException 없이 동작해야 함 (merge function 회귀 방지)
    @Test
    void replaceInstruments_toleratesLegacyDuplicateInstrumentNames() {
        post.addInstrument(PostInstrument.of(post, "바이올린", 3)); // 중복 이름 유입 시뮬레이션

        assertThatCode(() -> post.replaceInstruments(List.of(PostInstrument.of(post, "바이올린", 2))))
                .doesNotThrowAnyException();
    }

    @Test
    void replaceInstruments_recomputesStatus() {
        post.confirmInstrument("첼로"); // 첼로 마감 → PARTIALLY_CLOSED

        // 바이올린을 제거하면 남은 악기(첼로)가 이미 마감 상태이므로 전체 CLOSED가 되어야 함
        post.replaceInstruments(List.of(PostInstrument.of(post, "첼로", 1)));

        assertThat(post.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    @Test
    void replaceInstruments_reducingCapacityBelowConfirmedClosesInstrument() {
        post.confirmInstrument("바이올린"); // confirmed=1, people=2 → 아직 미마감

        post.replaceInstruments(List.of(
                PostInstrument.of(post, "바이올린", 1), // 정원 축소
                PostInstrument.of(post, "첼로", 1)
        ));

        assertThat(instrument("바이올린").getClosed()).isTrue();
        assertThat(instrument("바이올린").getConfirmed()).isEqualTo(1);
    }

    @Test
    void replaceInstruments_respectsManuallyClosedStatus() {
        post.close(); // 수동 마감

        post.replaceInstruments(List.of(PostInstrument.of(post, "바이올린", 2)));

        assertThat(post.getStatus()).isEqualTo(PostStatus.CLOSED);
    }

    // BACKLOG.md #34: 모집글 상세 "설명" 섹션에 대응하는 필드
    @Test
    void create_setsDescriptionFromInfo() {
        User owner = User.builder().id(1L).nickname("owner").firebaseUid("owner-uid").build();

        Post created = Post.create(owner, PostInfo.builder().description("공연 설명입니다").build());

        assertThat(created.getDescription()).isEqualTo("공연 설명입니다");
    }

    @Test
    void updateInfo_updatesDescription() {
        User owner = User.builder().id(1L).nickname("owner").firebaseUid("owner-uid").build();
        Post created = Post.create(owner, PostInfo.builder().description("기존 설명").build());

        created.updateInfo(PostInfo.builder().description("수정된 설명").build());

        assertThat(created.getDescription()).isEqualTo("수정된 설명");
    }

    // BACKLOG.md #38: location 자유텍스트와 분리된 지역 필터 전용 컬럼
    @Test
    void create_setsRegionFromInfo() {
        User owner = User.builder().id(1L).nickname("owner").firebaseUid("owner-uid").build();

        Post created = Post.create(owner, PostInfo.builder().region("서울").build());

        assertThat(created.getRegion()).isEqualTo("서울");
    }

    @Test
    void updateInfo_updatesRegion() {
        User owner = User.builder().id(1L).nickname("owner").firebaseUid("owner-uid").build();
        Post created = Post.create(owner, PostInfo.builder().region("서울").build());

        created.updateInfo(PostInfo.builder().region("경기").build());

        assertThat(created.getRegion()).isEqualTo("경기");
    }

    // CLAUDE.md §8: ApplicationAccessPolicy가 post.getUser().getId().equals(...) 체인을 직접 다루지 않도록
    // 소유자 판단 로직 자체를 Post로 이관(Tell-Don't-Ask)
    @Test
    void isOwnedBy_trueWhenSameUser() {
        User owner = User.builder().id(1L).nickname("owner").firebaseUid("owner-uid").build();
        Post created = Post.create(owner, PostInfo.builder().build());

        assertThat(created.isOwnedBy(owner)).isTrue();
    }

    @Test
    void isOwnedBy_falseWhenDifferentUser() {
        User owner = User.builder().id(1L).nickname("owner").firebaseUid("owner-uid").build();
        User other = User.builder().id(2L).nickname("other").firebaseUid("other-uid").build();
        Post created = Post.create(owner, PostInfo.builder().build());

        assertThat(created.isOwnedBy(other)).isFalse();
    }
}
