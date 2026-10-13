package com.wangnu.allargando.user.service;

import com.wangnu.allargando.global.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 이름 규칙: 한글·영문과 단어 사이 공백 한 칸만, 2~30자, NFC 정규화
class NamePolicyTest {

    @Test
    void normalizeAndValidate_acceptsKoreanAndEnglishNames() {
        assertThat(NamePolicy.normalizeAndValidate("홍길동")).isEqualTo("홍길동");
        assertThat(NamePolicy.normalizeAndValidate("John Smith")).isEqualTo("John Smith");
    }

    @Test
    void normalizeAndValidate_stripsOuterWhitespaceAndNormalizesToNfc() {
        String nfd = " " + Normalizer.normalize("한글", Normalizer.Form.NFD) + " ";

        assertThat(NamePolicy.normalizeAndValidate(nfd)).isEqualTo("한글");
    }

    @Test
    void normalizeAndValidate_rejectsDigitsSymbolsAndExtraSpaces() {
        for (String bad : new String[]{"홍길동1", "홍_길동", "홍  길동", "홍길동!", "홍길동😀", "ㅎㅎ"}) {
            assertThatThrownBy(() -> NamePolicy.normalizeAndValidate(bad))
                    .as(bad).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void normalizeAndValidate_rejectsTooShortTooLongAndNull() {
        assertThatThrownBy(() -> NamePolicy.normalizeAndValidate("홍")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> NamePolicy.normalizeAndValidate("가".repeat(31))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> NamePolicy.normalizeAndValidate(null)).isInstanceOf(BadRequestException.class);
        assertThat(NamePolicy.normalizeAndValidate("가".repeat(30))).hasSize(30);
    }
}
