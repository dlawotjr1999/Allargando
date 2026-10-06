package com.wangnu.allargando.global.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageSupportTest {

    // 클라이언트가 보낸 정렬은 버리고 page·size만 유지한다
    @Test
    void withSort_replacesClientSortAndKeepsPaging() {
        Pageable requested = PageRequest.of(3, 20, Sort.by("user.phoneNumber"));

        Pageable result = PageSupport.withSort(requested, Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(result.getPageNumber()).isEqualTo(3);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    // 같은 시작일의 공연이 여러 개여도 페이지를 넘길 때 순서가 흔들리지 않도록 id를 마지막 기준으로 덧붙인다
    @Test
    void withSort_appendsIdAsFinalTiebreaker() {
        Pageable result = PageSupport.withSort(PageRequest.of(0, 10), Sort.by(Sort.Direction.ASC, "startDate"));

        assertThat(result.getSort().stream().map(Sort.Order::getProperty)).containsExactly("startDate", "id");
        assertThat(result.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    // 이미 id로 정렬하는 요청에는 id를 또 붙이지 않는다
    @Test
    void withSort_doesNotDuplicateIdWhenAlreadySortedById() {
        Sort byIdDesc = Sort.by(Sort.Direction.DESC, "id");

        Pageable result = PageSupport.withSort(PageRequest.of(0, 10), byIdDesc);

        assertThat(result.getSort()).isEqualTo(byIdDesc);
    }

    // 정렬을 Specification이 직접 거는 목록은 정렬 없이 넘기므로, id를 붙여 그 정렬을 덮어쓰면 안 된다
    @Test
    void withSort_keepsUnsortedWhenNoSortRequested() {
        Pageable result = PageSupport.withSort(PageRequest.of(2, 10, Sort.by("anything")), Sort.unsorted());

        assertThat(result.getSort().isUnsorted()).isTrue();
        assertThat(result.getPageNumber()).isEqualTo(2);
    }
}
