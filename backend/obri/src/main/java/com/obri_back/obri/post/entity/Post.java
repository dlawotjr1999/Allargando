package com.obri_back.obri.post.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import com.obri_back.obri.global.exception.BadRequestException;
import com.obri_back.obri.user.entity.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 모집글 엔티티
 * 작성자(User)를 단방향 참조하고 PostInstrument와 양방향 1:N 소유.
 * 악기 확정·마감·글 전체 상태(OPEN/PARTIALLY_CLOSED/CLOSED) 전이를 도메인 메서드로 관리
 */
@Getter
@NoArgsConstructor
@Entity
@Table (name = "post")
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false)  
    private String title;

    @Column(name = "category", nullable = false)
    private String category;

    // 목록 조회 시 글마다 악기를 개별 로딩(N+1)하지 않도록 IN 절로 한 번에 배치 로딩
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PostInstrument> postInstruments = new ArrayList<>();

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @Column(name = "location", nullable = false)
    private String location;

    // 지역 필터 전용 컬럼 — location(자유 텍스트 주소)과 분리, 작성 시 프론트가 제공하는 지역 목록 중 선택(BACKLOG.md #38)
    @Column(name = "region", nullable = false)
    private String region;

    @Column(name = "timetable" , nullable = false)
    private String timetable;

    // 모집글 상세 설명 (선택 입력) — BACKLOG.md #34, 프론트 상세 화면 "설명" 섹션에 대응
    // length만 지정(columnDefinition 미사용) — DB별 raw SQL 타입(TEXT 등)에 의존하지 않아 이식성 확보
    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PostStatus status;

    // 작성자가 수동으로 전체 마감했는지 여부 — true면 recomputeStatus()가 악기별 파생 상태로 되돌리지 않음(BACKLOG.md #31)
    @Column(name = "manually_closed", nullable = false)
    private Boolean manuallyClosed;

    // 낙관적 락
    @Version
    @Column(name = "version")
    private Long version;                

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // 모집글 생성 (악기 목록은 호출부에서 addInstrument로 추가). 초기 상태 OPEN
    public static Post create(User user, PostInfo info) {
        Post post = new Post();
        post.user = user;
        post.title = info.getTitle();
        post.category = info.getCategory();
        post.eventAt = info.getEventAt();
        post.location = info.getLocation();
        post.region = info.getRegion();
        post.timetable = info.getTimetable();
        post.description = info.getDescription();
        post.status = PostStatus.OPEN;
        post.manuallyClosed = false;
        return post;
    }

    // 모집 악기 1건 추가 (양방향 연관의 부모 쪽 편의 메서드)
    public void addInstrument(PostInstrument instrument) {
        this.postInstruments.add(instrument);
    }

    // 글 본문 정보 수정 (악기 목록 교체는 replaceInstruments에서 별도 처리)
    public void updateInfo(PostInfo info) {
        this.category = info.getCategory();
        this.title = info.getTitle();
        this.eventAt = info.getEventAt();
        this.location = info.getLocation();
        this.region = info.getRegion();
        this.timetable = info.getTimetable();
        this.description = info.getDescription();
    }

    // 악기 목록 교체 (글 수정) — 이름이 같은 악기는 확정 인원(confirmed)·마감 상태를 승계하고 정원만 갱신,
    // 새 목록에서 사라진 이름만 제거(orphanRemoval), 새로 등장한 이름만 추가. 전체 clear 후 재삽입하면
    // 이미 수락된 지원자의 확정 카운트가 초기화되는 문제가 있었음(BACKLOG.md #32)
    // 정원을 수락된 인원 밑으로 줄이는 요청은 400(D12) — 변경을 시작하기 전에 전부 검증해 부분 적용 없이 거부한다
    public void replaceInstruments(List<PostInstrument> newInstruments) {
        Map<String, PostInstrument> existingByName = this.postInstruments.stream()
                .collect(Collectors.toMap(PostInstrument::getInstrument, pi -> pi, (a, b) -> a));
        Set<String> newNames = newInstruments.stream()
                .map(PostInstrument::getInstrument)
                .collect(Collectors.toSet());

        for (PostInstrument newInstrument : newInstruments) {
            PostInstrument existing = existingByName.get(newInstrument.getInstrument());
            if (existing != null) {
                existing.requireCapacityAtLeastConfirmed(newInstrument.getPeople());
            }
        }

        this.postInstruments.removeIf(pi -> !newNames.contains(pi.getInstrument()));

        for (PostInstrument newInstrument : newInstruments) {
            PostInstrument existing = existingByName.get(newInstrument.getInstrument());
            if (existing != null) {
                existing.updatePeople(newInstrument.getPeople());
            } else {
                addInstrument(newInstrument);
            }
        }

        recomputeStatus();
    }

    // 수동 전체 마감 — 상태를 CLOSED로 전환하고, 이후 악기 상태 변동(철회 등)에 재파생되지 않도록 플래그 고정
    public void close() {
        this.manuallyClosed = true;
        this.status = PostStatus.CLOSED;
    }

    // 지원 수락 시: 해당 악기 확정 인원 증가 후 전체 상태 재계산 (Post 도메인 로직)
    // 지원자 전공이 모집 목록에 없으면 자리 미반영·상태만 수락 허용(시나리오 1.4) → 조용히 무동작
    public void confirmInstrument(String instrumentName) {
        PostInstrument target = findInstrument(instrumentName);
        if (target == null) {
            return; // 모집하지 않는 악기: 자리 카운트 미반영, 상태만 ACCEPTED
        }
        if (Boolean.TRUE.equals(target.getClosed())) {
            throw new BadRequestException("이미 정원이 마감된 악기입니다");
        }
        target.confirm();
        recomputeStatus();
    }

    // 수락 철회 시: 해당 악기 확정 인원 감소·정원 기준 마감 재계산 후 전체 상태 재계산(재오픈)
    // 미반영 수락(모집 목록에 없는 악기)의 철회는 되돌릴 자리가 없으므로 무동작
    public void revokeInstrument(String instrumentName) {
        PostInstrument target = findInstrument(instrumentName);
        if (target == null) {
            return; // 자리 미반영 수락의 철회: 변동 없음
        }
        target.revoke();
        recomputeStatus();
    }

    // 이 글의 작성자가 user인지 확인 — 호출부가 getUser().getId().equals(...) 체인을 직접 다루지 않도록 함(CLAUDE.md §8)
    public boolean isOwnedBy(User user) {
        return this.user.getId().equals(user.getId());
    }

    // 해당 악기가 이미 정원 마감됐는지 확인 — 모집 목록에 없는 악기는 마감 개념이 없어 false(자리 미반영 지원 허용, 시나리오 1.4)
    public boolean isInstrumentClosed(String instrumentName) {
        PostInstrument target = findInstrument(instrumentName);
        return target != null && Boolean.TRUE.equals(target.getClosed());
    }

    // 모집 목록에 해당 악기가 없으면 null (호출부에서 "미반영" 케이스로 처리)
    private PostInstrument findInstrument(String instrumentName) {
        return this.postInstruments.stream()
                .filter(pi -> pi.getInstrument().equals(instrumentName))
                .findFirst()
                .orElse(null);
    }

    // 악기별 마감 상태로부터 글 전체 상태를 파생: 전부 마감→CLOSED, 일부→PARTIALLY_CLOSED, 없음→OPEN
    // 단, 작성자가 수동 마감(manuallyClosed)한 글은 악기 상태가 바뀌어도(예: 수락 철회) 재오픈하지 않음(BACKLOG.md #31)
    private void recomputeStatus() {
        if (Boolean.TRUE.equals(this.manuallyClosed)) {
            this.status = PostStatus.CLOSED;
            return;
        }
        boolean allClosed = this.postInstruments.stream().allMatch(pi -> Boolean.TRUE.equals(pi.getClosed()));
        boolean anyClosed = this.postInstruments.stream().anyMatch(pi -> Boolean.TRUE.equals(pi.getClosed()));
        if (allClosed) {
            this.status = PostStatus.CLOSED;
        } else if (anyClosed) {
            this.status = PostStatus.PARTIALLY_CLOSED;
        } else {
            this.status = PostStatus.OPEN;
        }
    }
}
