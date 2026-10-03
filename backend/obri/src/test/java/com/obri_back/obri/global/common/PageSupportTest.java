package com.obri_back.obri.global.common;

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
        assertThat(result.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
