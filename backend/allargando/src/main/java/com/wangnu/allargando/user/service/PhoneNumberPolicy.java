package com.wangnu.allargando.user.service;

import com.wangnu.allargando.global.exception.BadRequestException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * 전화번호 규칙 — Firebase ID 토큰의 phone_number는 국제 표기(E.164, 예: +821012345678)로 오는데,
 * 서비스는 번호를 "010-1234-5678" 한 가지 형식으로만 저장·비교·표시한다. 같은 번호가 표기만 달라 중복 가입되지 않게 하고
 * (UNIQUE는 문자열 그대로 비교한다), 지원자 번호 마스킹(010-****-5678)도 이 형식을 전제로 하기 때문이다.
 * 서비스 대상이 국내 사용자라 국내 휴대폰 번호(010·011·016·017·018·019)만 받는다.
 */
public final class PhoneNumberPolicy {

    static final String INVALID_MESSAGE = "국내 휴대폰 번호로만 인증할 수 있습니다";

    // +82 다음에 앞자리 0을 뗀 번호가 온다: 휴대폰 식별번호(10·11·16·17·18·19) + 가입자 번호 7~8자리
    private static final Pattern KOREAN_MOBILE_E164 = Pattern.compile("^\\+82(1[016789])(\\d{7,8})$");

    private PhoneNumberPolicy() {
    }

    /*
     * 국제 표기의 국내 휴대폰 번호를 저장 형식으로 바꾼다.
     * 가입자 번호가 8자리면 010-1234-5678, 7자리면(구형 011·016·017·018·019 일부) 011-123-4567 형태가 된다.
     *
     * @param e164 +821012345678 같은 국제 표기 번호
     * @return 010-1234-5678 형식 번호
     * @throws BadRequestException null이거나 국내 휴대폰 번호가 아닐 때(400)
     */
    public static String fromE164(String e164) {
        if (e164 == null) {
            throw new BadRequestException(INVALID_MESSAGE);
        }
        Matcher matcher = KOREAN_MOBILE_E164.matcher(e164.strip());
        if (!matcher.matches()) {
            throw new BadRequestException(INVALID_MESSAGE);
        }
        String prefix = "0" + matcher.group(1);
        String subscriber = matcher.group(2);
        int split = subscriber.length() - 4;
        return prefix + "-" + subscriber.substring(0, split) + "-" + subscriber.substring(split);
    }
}
