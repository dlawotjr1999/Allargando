package com.obri_back.obri.user.service;

import com.obri_back.obri.global.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 닉네임 규칙(D3): 한글·영문·숫자·_ 2~20자, trim+NFC 정규화, 예약어 차단
class NicknamePolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"tester", "바이올리니스트", "Cello_99", "가나", "a1", "한글English_123"})
    void normalizeAndValidate_acceptsAllowedNicknames(String nickname) {
        assertThat(NicknamePolicy.normalizeAndValidate(nickname)).isEqualTo(nickname);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "a", "a/b", "50%off", "a.b", "a b", "a-b", "이모지😀", "ㅋㅋ", "123456789012345678901"})
    void normalizeAndValidate_rejectsInvalidFormat(String nickname) {
        assertThatThrownBy(() -> NicknamePolicy.normalizeAndValidate(nickname))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("사용할 수 없는 닉네임입니다");
    }

    // 예약어는 대소문자를 가리지 않고, 형식 오류와 같은 문구로 거절한다
    @ParameterizedTest
    @ValueSource(strings = {"me", "ME", "Admin", "administrator", "root", "system", "관리자", "운영자", "운영팀", "공식", "Obri", "PocoAPoco"})
    void normalizeAndValidate_rejectsReservedWords(String nickname) {
        assertThatThrownBy(() -> NicknamePolicy.normalizeAndValidate(nickname))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("사용할 수 없는 닉네임입니다");
    }

    @Test
    void normalizeAndValidate_trimsSurroundingWhitespace() {
        assertThat(NicknamePolicy.normalizeAndValidate("  tester  ")).isEqualTo("tester");
    }

    @Test
    void normalizeAndValidate_convertsNfdHangulToNfc() {
        String nfd = Normalizer.normalize("한글닉", Normalizer.Form.NFD);

        assertThat(nfd).isNotEqualTo("한글닉");
        assertThat(NicknamePolicy.normalizeAndValidate(nfd)).isEqualTo("한글닉");
    }

    // 조회용 normalize는 검증하지 않는다 — 규칙을 어기는 기존 닉네임도 찾을 수 있어야 한다
    @Test
    void normalize_doesNotValidate() {
        assertThat(NicknamePolicy.normalize(" a/b ")).isEqualTo("a/b");
    }

    // 길이 경계: 20자는 허용, 21자는 거절
    @Test
    void normalizeAndValidate_enforcesLengthBoundary() {
        String twenty = "a".repeat(20);

        assertThat(NicknamePolicy.normalizeAndValidate(twenty)).isEqualTo(twenty);
        assertThatThrownBy(() -> NicknamePolicy.normalizeAndValidate("a".repeat(21)))
                .isInstanceOf(BadRequestException.class);
    }
}
