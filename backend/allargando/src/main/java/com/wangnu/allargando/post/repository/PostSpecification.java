package com.wangnu.allargando.post.repository;

import com.wangnu.allargando.block.entity.UserBlock;
import com.wangnu.allargando.post.entity.Post;
import com.wangnu.allargando.post.entity.PostInstrument;
import com.wangnu.allargando.post.entity.PostStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/*
 * 모집글 목록 동적 필터 명세 빌더
 * GET /api/posts 필터: 필터 간 AND, 같은 필터 내 다중값은 OR
 * eventAt이 지난 글은 항상 제외(공연 종료 후 목록 노출 방지) — status로도 우회 불가
 * status(복수)는 기본 OPEN·PARTIALLY_CLOSED이고, CLOSED를 고르면 마감된 글도 보인다(D15)
 * 조회하는 유저(viewerId)가 차단한 유저의 글은 항상 제외 — viewerId가 null이면 이 조건을 건너뛴다
 * 악기 필터는 JOIN+DISTINCT 대신 EXISTS 서브쿼리를 쓴다 — 정렬식(마감 임박순의 서브쿼리)이 SELECT DISTINCT와
 * 충돌하고 count(distinct) 비용도 줄어든다. 마감된 악기도 매칭한다(D14 4b)
 */
public class PostSpecification {

    // 상태 파라미터가 없을 때의 기본 노출 상태 — 모집 중인 글만
    private static final List<PostStatus> DEFAULT_STATUSES = List.of(PostStatus.OPEN, PostStatus.PARTIALLY_CLOSED);

    // 기본 상태·정렬 없음으로 필터만 적용(정렬은 호출부가 정한다). 기존 호출부 호환용
    public static Specification<Post> filter(Long viewerId, List<String> categories, List<String> instruments,
            List<String> regions, LocalDate startDate, LocalDate endDate) {
        return filter(viewerId, categories, instruments, regions, startDate, endDate, null, null);
    }

    // 카테고리·악기·지역·기간·상태 조건을 조합하고 sort가 있으면 정렬까지 지정한 Specification 생성.
    // 정렬은 쿼리에 직접 건다(count 쿼리에는 걸지 않는다) — 호출부는 정렬 없는 Pageable을 넘겨야 이 정렬이 유지된다
    public static Specification<Post> filter(Long viewerId, List<String> categories, List<String> instruments,
            List<String> regions, LocalDate startDate, LocalDate endDate, List<PostStatus> statuses, PostSort sort) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 내가 차단한 유저의 글 제외 — "차단 기록이 존재하지 않는" 글만 남기는 NOT EXISTS 서브쿼리
            if (viewerId != null) {
                Subquery<Long> blockedByViewer = query.subquery(Long.class);
                Root<UserBlock> block = blockedByViewer.from(UserBlock.class);
                blockedByViewer.select(block.get("id")).where(
                        cb.equal(block.get("blocker").get("id"), viewerId),
                        cb.equal(block.get("blocked").get("id"), root.get("user").get("id")));
                predicates.add(cb.not(cb.exists(blockedByViewer)));
            }

            // 공연 날짜가 지난 글은 항상 제외 — status 필터로도 우회 불가
            predicates.add(cb.greaterThanOrEqualTo(root.get("eventAt"), LocalDateTime.now()));

            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").in(categories));
            }

            // 고른 악기 중 하나라도 모집하는 글 — 행을 늘리지 않는 EXISTS라 DISTINCT가 필요 없다
            if (instruments != null && !instruments.isEmpty()) {
                Subquery<Long> matchingInstrument = query.subquery(Long.class);
                Root<PostInstrument> pi = matchingInstrument.from(PostInstrument.class);
                matchingInstrument.select(pi.get("id")).where(
                        cb.equal(pi.get("post"), root),
                        pi.get("instrument").in(instruments));
                predicates.add(cb.exists(matchingInstrument));
            }

            if (regions != null && !regions.isEmpty()) {
                predicates.add(root.get("region").in(regions));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventAt"), startDate.atStartOfDay()));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventAt"), endDate.atTime(LocalTime.MAX)));
            }

            // 상태: 고른 값이 없으면 모집 중인 글만, 고르면 그 상태들만(CLOSED를 고르면 마감 글이 보인다)
            List<PostStatus> effectiveStatuses = (statuses == null || statuses.isEmpty()) ? DEFAULT_STATUSES : statuses;
            predicates.add(root.get("status").in(effectiveStatuses));

            // count 쿼리(결과 타입이 Long)에는 정렬을 걸지 않는다
            if (sort != null && !Long.class.equals(query.getResultType())) {
                query.orderBy(orderOf(sort, root, query, cb));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // 정렬 기준별 ORDER BY. 같은 값끼리는 항상 같은 순서가 나오도록 보조 정렬(공연일·id)을 덧붙인다
    private static List<Order> orderOf(PostSort sort, Root<Post> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        return switch (sort) {
            case EVENT_SOON -> List.of(cb.asc(root.get("eventAt")), cb.desc(root.get("id")));
            case CLOSING_SOON -> {
                // 남은 자리 = 이 글의 모든 악기의 (모집 인원 - 확정 인원) 합. 적을수록 마감이 임박한 글
                Subquery<Integer> remaining = query.subquery(Integer.class);
                Root<PostInstrument> pi = remaining.from(PostInstrument.class);
                remaining.select(cb.sum(cb.diff(pi.<Integer>get("people"), pi.<Integer>get("confirmed"))))
                        .where(cb.equal(pi.get("post"), root));
                yield List.of(cb.asc(remaining), cb.asc(root.get("eventAt")), cb.desc(root.get("id")));
            }
            case LATEST -> List.of(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        };
    }
}
