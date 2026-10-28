package com.wangnu.allargando.global.common;

import com.wangnu.allargando.global.exception.BadRequestException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordSearchTest {

    // 앞뒤 공백은 지우고, 비었거나 공백뿐이면 검색하지 않는다(null)
    @Test
    void normalize_stripsAndTreatsBlankAsNoSearch() {
        assertThat(KeywordSearch.normalize("  재즈 공연 ")).isEqualTo("재즈 공연");
        assertThat(KeywordSearch.normalize("   ")).isNull();
        assertThat(KeywordSearch.normalize("")).isNull();
        assertThat(KeywordSearch.normalize(null)).isNull();
    }

    // 최대 길이까지는 통과, 넘으면 400(공백 정리 뒤 길이로 판단)
    @Test
    void normalize_rejectsKeywordLongerThanMaxLength() {
        String max = "가".repeat(KeywordSearch.MAX_LENGTH);

        assertThat(KeywordSearch.normalize(" " + max + " ")).isEqualTo(max);
        assertThatThrownBy(() -> KeywordSearch.normalize(max + "가"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(String.valueOf(KeywordSearch.MAX_LENGTH));
    }

    // LIKE 특수문자는 글자 그대로 찾도록 이스케이프한다(이스케이프 문자 자신을 먼저 처리)
    @Test
    void escapeLike_escapesWildcardsAndEscapeChar() {
        assertThat(KeywordSearch.escapeLike("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
        assertThat(KeywordSearch.escapeLike("재즈")).isEqualTo("재즈");
    }
}
