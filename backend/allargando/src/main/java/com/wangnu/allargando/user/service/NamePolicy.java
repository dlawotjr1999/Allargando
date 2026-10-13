package com.wangnu.allargando.user.service;

import com.wangnu.allargando.global.exception.BadRequestException;

import java.text.Normalizer;
import java.util.regex.Pattern;

/*
 * 이름(실명) 규칙 — 모집자에게 보이는 값이라 닉네임보다 느슨하되 숫자·기호·이모지는 받지 않는다.
 * 앞뒤 공백을 자르고 NFC로 정규화한 뒤 한글(완성형)·영문과 단어 사이 공백 한 칸만 허용한다(2~30자).
 * 닉네임과 달리 UNIQUE가 아니며 조회·차단·신고의 식별자로 쓰지 않는다.
 */
public final class NamePolicy {

    static final String INVALID_MESSAGE = "이름은 한글 또는 영문 2~30자로 입력해주세요";

    private static final Pattern FORMAT =
            Pattern.compile("^[가-힣A-Za-z]+(?: [가-힣A-Za-z]+)*$");
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 30;

    private NamePolicy() {
    }

    /*
     * 이름을 정규화하고 형식을 검증한다.
     * param  : name 요청에서 받은 이름
     * return : 정규화된 이름
     * throws : BadRequestException 형식이 맞지 않을 때
     */
    public static String normalizeAndValidate(String name) {
        if (name == null) {
            throw new BadRequestException(INVALID_MESSAGE);
        }
        String normalized = Normalizer.normalize(name.strip(), Normalizer.Form.NFC);
        int length = normalized.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new BadRequestException(INVALID_MESSAGE);
        }
        return normalized;
    }
}
