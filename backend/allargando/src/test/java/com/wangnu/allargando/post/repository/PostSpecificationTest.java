package com.wangnu.allargando.post.repository;

import com.wangnu.allargando.block.entity.UserBlock;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInfo;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.post.entity.PostStatus;
import com.wangnu.allargando.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// PostSpecification의 region 필터는 Criteria API 조건이라 실제 쿼리 실행 없이는 검증 불가 → @DataJpaTest(내장 H2)
// BACKLOG.md #38: location 자유텍스트 LIKE 매칭 대신 region 전용 컬럼 정확 일치로 전환
// globally_quoted_identifiers: `user` 테이블명이 H2 예약어(USER)와 충돌해 스키마 생성 자체가 실패하는 문제 회피
// spring.flyway.enabled=false: Flyway가 MySQL 문법 마이그레이션을 이 H2 스키마에 적용하려 들면
// 충돌·실패하므로, 이 테스트는 지금처럼 Hibernate가 H2 스키마를 직접 관리(ddl-auto=create-drop, @DataJpaTest 기본값)하도록 유지
// ddl-auto=create-drop 명시: @DataJpaTest의 기본값(create-drop)은 application.properties의
// spring.jpa.hibernate.ddl-auto=validate에 덮여 무력화된다(CI도 동일 값을 환경변수로 주입).
// 그 결과 비어 있는 H2 스키마를 validate하려다 SchemaManagementException으로 실패하므로 여기서 되돌린다
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PostSpecificationTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private PostRepository postRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .firebaseUid("owner-uid")
                .phoneNumber("010-0000-0000")
                .nickname("owner")
                .instrument("바이올린")
                .build();
        entityManager.persist(owner);
    }

    private void persistPost(String region, String location) {
        Post post = Post.create(owner, PostInfo.builder()
                .category("앙상블")
                .title("제목")
                .eventAt(LocalDateTime.now().plusDays(7))
                .location(location)
                .region(region)
                .timetable("13:00")
                .build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 1));
        entityManager.persist(post);
    }

    @Test
    void filter_matchesExactRegion() {
        persistPost("서울", "서울 강남구 OO홀");
        persistPost("경기", "경기 성남시 OO홀");

        List<Post> result = postRepository.findAll(
                PostSpecification.filter(null, null, null, List.of("서울"), null, null));

        assertThat(result).extracting(Post::getRegion).containsExactly("서울");
    }

    // location 텍스트에 지역명이 포함돼도 region 필드가 다르면 매칭되지 않아야 함 — LIKE 매칭 시절의 버그 재발 방지
    @Test
    void filter_doesNotMatchBySubstringOfLocation() {
        persistPost("경기", "서울 접경 경기 광주시 OO홀");

        List<Post> result = postRepository.findAll(
                PostSpecification.filter(null, null, null, List.of("서울"), null, null));

        assertThat(result).isEmpty();
    }

    @Test
    void filter_multipleRegionsActAsOr() {
        persistPost("서울", "서울 강남구 OO홀");
        persistPost("경기", "경기 성남시 OO홀");
        persistPost("부산", "부산 해운대구 OO홀");

        List<Post> result = postRepository.findAll(
                PostSpecification.filter(null, null, null, List.of("서울", "경기"), null, null));

        assertThat(result).extracting(Post::getRegion).containsExactlyInAnyOrder("서울", "경기");
    }

    @Test
    void filter_noRegionParam_returnsAllRegions() {
        persistPost("서울", "서울 강남구 OO홀");
        persistPost("경기", "경기 성남시 OO홀");

        List<Post> result = postRepository.findAll(PostSpecification.filter(null, null, null, null, null, null));

        assertThat(result).hasSize(2);
    }

    // 조회하는 유저가 차단한 작성자의 글은 목록에서 빠지고, 다른 작성자의 글과 차단하지 않은 유저의 시점은 영향받지 않는다
    @Test
    void filter_excludesPostsOfAuthorsBlockedByViewer() {
        User blockedAuthor = User.builder()
                .firebaseUid("blocked-uid").phoneNumber("010-1111-1111").nickname("blocked").instrument("첼로").build();
        entityManager.persist(blockedAuthor);
        User viewer = User.builder()
                .firebaseUid("viewer-uid").phoneNumber("010-2222-2222").nickname("viewer").instrument("피아노").build();
        entityManager.persist(viewer);
        User bystander = User.builder()
                .firebaseUid("bystander-uid").phoneNumber("010-3333-3333").nickname("bystander").instrument("플루트").build();
        entityManager.persist(bystander);

        persistPostBy(owner, "정상 글");
        persistPostBy(blockedAuthor, "차단당한 사람의 글");
        entityManager.persist(UserBlock.of(viewer, blockedAuthor));
        entityManager.flush();

        List<Post> seenByViewer = postRepository.findAll(
                PostSpecification.filter(viewer.getId(), null, null, null, null, null));
        List<Post> seenByBystander = postRepository.findAll(
                PostSpecification.filter(bystander.getId(), null, null, null, null, null));

        assertThat(seenByViewer).extracting(Post::getTitle).containsExactly("정상 글");
        assertThat(seenByBystander).extracting(Post::getTitle)
                .containsExactlyInAnyOrder("정상 글", "차단당한 사람의 글");
    }

    // 차단은 방향이 있다 — 내가 상대를 차단했다고 상대 시점에서 내 글이 사라지지는 않는다
    @Test
    void filter_blockIsDirectional() {
        User blocker = User.builder()
                .firebaseUid("blocker-uid").phoneNumber("010-4444-4444").nickname("blocker").instrument("첼로").build();
        entityManager.persist(blocker);
        persistPostBy(blocker, "차단한 사람의 글");
        entityManager.persist(UserBlock.of(blocker, owner));
        entityManager.flush();

        List<Post> seenByBlocked = postRepository.findAll(
                PostSpecification.filter(owner.getId(), null, null, null, null, null));

        assertThat(seenByBlocked).extracting(Post::getTitle).containsExactly("차단한 사람의 글");
    }

    // ---- D15: 정렬 3종·상태 파라미터·악기 EXISTS 필터 -------------------------------------------------

    // 공연일·악기(이름→[모집 인원, 확정 인원])를 지정해 글을 저장한다. 확정은 Post.confirmInstrument로 올려
    // 악기·글 상태가 실제 도메인 로직대로 파생되게 한다. closeManually면 수동 마감한다
    private Post persistDetailed(String title, LocalDateTime eventAt, boolean closeManually,
            java.util.Map<String, int[]> instruments) {
        Post post = Post.create(owner, PostInfo.builder()
                .category("앙상블")
                .title(title)
                .eventAt(eventAt)
                .location("서울 강남구")
                .region("서울")
                .timetable("13:00")
                .build());
        instruments.forEach((name, slots) -> post.addInstrument(PostInstrument.of(post, name, slots[0])));
        instruments.forEach((name, slots) -> {
            for (int i = 0; i < slots[1]; i++) {
                post.confirmInstrument(name);
            }
        });
        if (closeManually) {
            post.close();
        }
        entityManager.persist(post);
        entityManager.flush();
        return post;
    }

    private java.util.Map<String, int[]> violin(int people, int confirmed) {
        return java.util.Map.of("바이올린", new int[] {people, confirmed});
    }

    private List<String> titlesOf(org.springframework.data.domain.Page<Post> page) {
        return page.getContent().stream().map(Post::getTitle).toList();
    }

    private org.springframework.data.domain.Page<Post> query(
            List<String> instruments, List<PostStatus> statuses, PostSort sort) {
        return postRepository.findAll(
                PostSpecification.filter(null, null, instruments, null, null, null, statuses, sort),
                org.springframework.data.domain.PageRequest.of(0, 10));
    }

    @Test
    void sort_eventSoon_ordersByNearestEventFirst() {
        persistDetailed("먼 공연", LocalDateTime.now().plusDays(10), false, violin(2, 0));
        persistDetailed("가까운 공연", LocalDateTime.now().plusDays(2), false, violin(2, 0));
        persistDetailed("중간 공연", LocalDateTime.now().plusDays(5), false, violin(2, 0));

        assertThat(titlesOf(query(null, null, PostSort.EVENT_SOON)))
                .containsExactly("가까운 공연", "중간 공연", "먼 공연");
    }

    // 기본 정렬은 최근 등록한 글이 먼저(같은 시각이면 id가 큰 글 먼저)
    @Test
    void sort_latest_ordersByNewestFirst() {
        persistDetailed("첫 번째", LocalDateTime.now().plusDays(3), false, violin(2, 0));
        persistDetailed("두 번째", LocalDateTime.now().plusDays(4), false, violin(2, 0));
        persistDetailed("세 번째", LocalDateTime.now().plusDays(5), false, violin(2, 0));

        assertThat(titlesOf(query(null, null, PostSort.LATEST)))
                .containsExactly("세 번째", "두 번째", "첫 번째");
    }

    // 남은 자리(모든 악기의 모집 인원 - 확정 인원 합)가 적은 글이 먼저
    @Test
    void sort_closingSoon_ordersByFewestRemainingSlotsFirst() {
        persistDetailed("자리 많음", LocalDateTime.now().plusDays(3), false, violin(5, 0)); // 남은 5
        persistDetailed("거의 마감", LocalDateTime.now().plusDays(3), false, violin(5, 4)); // 남은 1
        persistDetailed("여러 악기", LocalDateTime.now().plusDays(3), false,
                java.util.Map.of("바이올린", new int[] {2, 0}, "첼로", new int[] {2, 1})); // 남은 3

        assertThat(titlesOf(query(null, null, PostSort.CLOSING_SOON)))
                .containsExactly("거의 마감", "여러 악기", "자리 많음");
    }

    // 정렬을 걸어도 count 쿼리가 깨지지 않고 전체 건수가 맞다(Page 조회가 count 쿼리를 따로 실행)
    @Test
    void sort_withPagination_keepsTotalCount() {
        persistDetailed("A", LocalDateTime.now().plusDays(1), false, violin(2, 0));
        persistDetailed("B", LocalDateTime.now().plusDays(2), false, violin(2, 0));
        persistDetailed("C", LocalDateTime.now().plusDays(3), false, violin(2, 0));

        org.springframework.data.domain.Page<Post> page = postRepository.findAll(
                PostSpecification.filter(null, null, null, null, null, null, null, PostSort.CLOSING_SOON),
                org.springframework.data.domain.PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void status_defaultExcludesClosedPosts() {
        persistDetailed("모집중", LocalDateTime.now().plusDays(3), false, violin(2, 0));
        persistDetailed("부분 마감", LocalDateTime.now().plusDays(3), false,
                java.util.Map.of("바이올린", new int[] {1, 1}, "첼로", new int[] {2, 0}));
        persistDetailed("수동 마감", LocalDateTime.now().plusDays(3), true, violin(2, 0));

        assertThat(titlesOf(query(null, null, PostSort.LATEST)))
                .containsExactlyInAnyOrder("모집중", "부분 마감");
    }

    @Test
    void status_closedSelectionIncludesClosedPosts() {
        persistDetailed("모집중", LocalDateTime.now().plusDays(3), false, violin(2, 0));
        persistDetailed("수동 마감", LocalDateTime.now().plusDays(3), true, violin(2, 0));
        persistDetailed("정원 마감", LocalDateTime.now().plusDays(3), false, violin(1, 1));

        assertThat(titlesOf(query(null, List.of(PostStatus.CLOSED), PostSort.LATEST)))
                .containsExactlyInAnyOrder("수동 마감", "정원 마감");
        assertThat(titlesOf(query(null, List.of(PostStatus.OPEN, PostStatus.CLOSED), PostSort.LATEST)))
                .containsExactlyInAnyOrder("모집중", "수동 마감", "정원 마감");
    }

    // 공연일이 지난 글은 상태를 어떻게 고르든 계속 제외된다
    @Test
    void status_pastEventStaysExcludedEvenWhenClosedSelected() {
        persistDetailed("지난 마감글", LocalDateTime.now().minusDays(1), true, violin(2, 0));
        persistDetailed("다가오는 마감글", LocalDateTime.now().plusDays(1), true, violin(2, 0));

        assertThat(titlesOf(query(null, List.of(PostStatus.CLOSED), PostSort.LATEST)))
                .containsExactly("다가오는 마감글");
    }

    // 악기 필터는 EXISTS라 여러 악기가 동시에 맞아도 글이 한 번만 나온다(DISTINCT 없이 중복 방지)
    @Test
    void instrumentFilter_returnsPostOnceEvenWhenSeveralInstrumentsMatch() {
        persistDetailed("두 악기 모집", LocalDateTime.now().plusDays(3), false,
                java.util.Map.of("바이올린", new int[] {2, 0}, "첼로", new int[] {2, 0}));
        persistDetailed("피아노만", LocalDateTime.now().plusDays(3), false,
                java.util.Map.of("피아노", new int[] {1, 0}));

        org.springframework.data.domain.Page<Post> page = query(List.of("바이올린", "첼로"), null, PostSort.LATEST);

        assertThat(titlesOf(page)).containsExactly("두 악기 모집");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    // D14 4b: 정원이 찬 악기로 걸러도 그 글이 보인다(마감된 악기도 악기 필터에 매칭)
    @Test
    void instrumentFilter_matchesInstrumentsThatAreAlreadyFull() {
        persistDetailed("바이올린 마감, 첼로 모집", LocalDateTime.now().plusDays(3), false,
                java.util.Map.of("바이올린", new int[] {1, 1}, "첼로", new int[] {2, 0}));

        assertThat(titlesOf(query(List.of("바이올린"), null, PostSort.LATEST)))
                .containsExactly("바이올린 마감, 첼로 모집");
    }

    private void persistPostBy(User author, String title) {
        Post post = Post.create(author, PostInfo.builder()
                .category("앙상블")
                .title(title)
                .eventAt(LocalDateTime.now().plusDays(7))
                .location("서울 강남구")
                .region("서울")
                .timetable("13:00")
                .build());
        post.addInstrument(PostInstrument.of(post, "바이올린", 1));
        entityManager.persist(post);
    }
}
