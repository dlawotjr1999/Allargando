package com.obri_back.obri.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.obri_back.obri.global.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 모집글의 모집 악기 엔티티 (Post 종속, 양방향 1:N의 자식)
 * 악기별 모집 인원(people)·확정 인원(confirmed)·마감 여부(closed)를 추적하며,
 * 악기 단위 @Version 낙관적 락으로 동시 수락 경합을 방지
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "post_instrument")
public class PostInstrument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "instrument", nullable = false)
    private String instrument;

    @Column(name = "people", nullable = false)
    private Integer people;

    @Column(name = "confirmed", nullable = false)
    private Integer confirmed;

    @Column(name = "closed", nullable = false)
    private Boolean closed;

    // 낙관적 락: 같은 Post 내 다른 악기끼리는 경합하지 않도록 악기 단위로 버전 관리
    @Version
    @Column(name = "version")
    private Long version;

    // 모집 악기 생성 (확정 인원 0, 미마감 상태로 초기화)
    public static PostInstrument of(Post post, String instrument, int people) {
        PostInstrument pi = new PostInstrument();
        pi.post = post;
        pi.instrument = instrument;
        pi.people = people;
        pi.confirmed = 0;
        pi.closed = false;
        return pi;
    }

    // 지원 수락: 확정 인원 1 증가, 정원 도달 시 악기 마감
    public void confirm() {
        this.confirmed++;
        if (this.confirmed >= this.people) {
            this.closed = true;
        }
    }

    // 수락 철회: 확정 인원 1 감소 후 마감 여부를 정원 기준으로 재계산한다(한 자리라도 비면 재오픈).
    // 무조건 재오픈(closed=false)하면 confirmed >= people인데 열린 상태가 되는 불일치가 생긴다
    public void revoke() {
        if (this.confirmed > 0) {
            this.confirmed--;
        }
        this.closed = this.confirmed >= this.people;
    }

    // 정원 변경 반영(글 수정 시 이름이 같은 악기 병합용): 이미 수락된 인원 밑으로는 줄일 수 없고(D12),
    // 확정 인원은 유지하며 새 정원 기준으로 마감 여부만 재계산
    public void updatePeople(int people) {
        requireCapacityAtLeastConfirmed(people);
        this.people = people;
        this.closed = this.confirmed >= this.people;
    }

    // 수락된 지원자가 있는 악기는 글 수정으로 삭제할 수 없다(400) — 확정된 자리가 조용히 사라지지 않게 한다(D12).
    // 먼저 지원자를 철회하면 삭제할 수 있다
    public void requireRemovable() {
        if (this.confirmed > 0) {
            throw new BadRequestException("'" + this.instrument + "'에 수락된 지원자가 " + this.confirmed
                    + "명 있어 악기를 삭제할 수 없어요. 먼저 철회해 주세요");
        }
    }

    // 새 정원이 확정 인원보다 작으면 400 — 수락된 지원자가 있는 자리를 조용히 없애지 않는다.
    // Post.replaceInstruments가 여러 악기를 바꾸기 전에 먼저 호출해 부분 변경 없이 거부할 수 있도록 공개한다
    public void requireCapacityAtLeastConfirmed(int newPeople) {
        if (newPeople < this.confirmed) {
            throw new BadRequestException("'" + this.instrument + "'에 수락된 지원자가 " + this.confirmed
                    + "명 있어 모집 인원을 " + this.confirmed + "명보다 적게 줄일 수 없어요. 먼저 철회해 주세요");
        }
    }
}
