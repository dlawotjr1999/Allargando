package com.wangnu.allargando.global.common;

import com.wangnu.allargando.global.exception.BadRequestException;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.Arrays;
import java.util.Locale;

/*
 * 목록 API의 검색어 공통 처리 — 연주회 검색이 쓰고, 모집글 등 다른 목록도 같은 규칙으로 붙일 수 있게 도메인 밖에 둔다.
 * 서비스는 normalize로 검색어를 정리·검증하고, Specification은 containsAny로 "고른 필드 중 하나라도 검색어를 포함"
 * 조건을 만든다. 어떤 필드에서 찾을지는 각 도메인이 정한다.
 */
public final class KeywordSearch {

    // 검색어 최대 길이 — 제목 일부를 찾는 용도라 이보다 길 이유가 없고, 긴 LIKE 패턴을 막는다
    public static final int MAX_LENGTH = 50;

    // LIKE 이스케이프 문자 — 검색어에 든 %·_가 와일드카드로 동작하지 않게 앞에 붙인다
    private static final char LIKE_ESCAPE = '\\';

    private KeywordSearch() {
    }

    // 검색어 정리 — 앞뒤 공백을 지우고 비면 검색하지 않는다(null). 최대 길이를 넘으면 400
    public static String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw new BadRequestException("검색어는 " + MAX_LENGTH + "자 이하로 입력해 주세요");
        }
        return trimmed;
    }

    // 필드 중 하나라도 검색어를 포함하면 참인 조건(대소문자 무시, %·_는 글자 그대로). keyword는 normalize를 거친 값이어야 한다
    @SafeVarargs
    public static Predicate containsAny(CriteriaBuilder cb, String keyword, Expression<String>... fields) {
        String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return cb.or(Arrays.stream(fields)
                .map(field -> cb.like(cb.lower(field), pattern, LIKE_ESCAPE))
                .toArray(Predicate[]::new));
    }

    // LIKE 특수문자(이스케이프 문자 자신·%·_)를 글자 그대로 찾도록 이스케이프한다
    static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
