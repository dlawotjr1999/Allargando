package com.wangnu.allargando.user.service;

import com.wangnu.allargando.global.exception.BadRequestException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/*
 * 닉네임 규칙(D3) — 가입·수정·중복 확인이 같은 규칙을 쓰도록 한 곳에 모은 유틸
 * 닉네임은 신고·차단·프로필의 경로 변수라 `/`·`%` 등을 막아야 하고, 한글은 NFD(자모 분리)로 들어올 수 있어
 * 검증 전에 trim + NFC 정규화를 먼저 한다(DTO의 @Pattern은 정규화 전 값을 보므로 쓰지 않는다).
 * 형식 오류와 예약어는 어느 쪽인지 드러내지 않도록 같은 문구로 거절한다.
 */
public final class NicknamePolicy {

    static final String INVALID_MESSAGE = "사용할 수 없는 닉네임입니다";

    // 한글(완성형)·영문·숫자·_ 2~20자. 공백·점·이모지·자모 단독(ㅋㅋ)은 불허
    private static final Pattern FORMAT = Pattern.compile("^[가-힣A-Za-z0-9_]{2,20}$");

    // 운영 사칭·경로 충돌을 막는 예약어(소문자). 목록 변경은 코드 수정 후 재배포 — 이미 쓰는 닉네임에는 영향 없음
    private static final Set<String> RESERVED = Set.of(
            "me", "admin", "administrator", "root", "system",
            "관리자", "운영자", "운영팀", "공식", "obri", "allargando", "알라르간도");

    private NicknamePolicy() {
    }

    /*
     * 정규화만 수행(trim + NFC). 이미 저장된 닉네임을 조회할 때 쓴다 — 기존 데이터가 규칙을 어길 수 있어 검증은 하지 않는다.
     */
    public static String normalize(String nickname) {
        return Normalizer.normalize(nickname.strip(), Normalizer.Form.NFC);
    }

    /*
     * 정규화 후 형식·예약어를 검증해 정규화된 닉네임을 돌려준다. 가입·수정·중복 확인 같은 새 입력에 쓴다.
     *
     * @throws BadRequestException null이거나 형식 위반이거나 예약어일 때(400)
     */
    public static String normalizeAndValidate(String nickname) {
        if (nickname == null) {
            throw new BadRequestException(INVALID_MESSAGE);
        }
        String normalized = normalize(nickname);
        if (!FORMAT.matcher(normalized).matches()
                || RESERVED.contains(normalized.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException(INVALID_MESSAGE);
        }
        return normalized;
    }
}
