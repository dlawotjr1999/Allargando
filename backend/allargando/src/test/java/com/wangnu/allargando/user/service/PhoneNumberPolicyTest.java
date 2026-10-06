package com.wangnu.allargando.user.service;

import com.wangnu.allargando.global.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberPolicyTest {

    // 가입자 번호가 8자리면 3-4-4 형식
    @Test
    void fromE164_formatsEightDigitSubscriberAsThreeFourFour() {
        assertThat(PhoneNumberPolicy.fromE164("+821012345678")).isEqualTo("010-1234-5678");
        assertThat(PhoneNumberPolicy.fromE164("+821000100001")).isEqualTo("010-0010-0001");
    }

    // 구형 번호의 가입자 번호가 7자리면 3-3-4 형식
    @Test
    void fromE164_formatsSevenDigitSubscriberAsThreeThreeFour() {
        assertThat(PhoneNumberPolicy.fromE164("+82111234567")).isEqualTo("011-123-4567");
    }

    @Test
    void fromE164_ignoresSurroundingWhitespace() {
        assertThat(PhoneNumberPolicy.fromE164("  +821012345678 ")).isEqualTo("010-1234-5678");
    }

    // 국내 휴대폰이 아니거나 형식이 틀리면 400
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", "010-1234-5678", "01012345678", "821012345678",
            "+14155550123", "+8221234567", "+82101234", "+8210123456789", "+821212345678"})
    void fromE164_throwsBadRequestWhenNotKoreanMobile(String input) {
        assertThatThrownBy(() -> PhoneNumberPolicy.fromE164(input))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PhoneNumberPolicy.INVALID_MESSAGE);
    }
}
