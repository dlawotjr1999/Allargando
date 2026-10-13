package com.wangnu.allargando.global.version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 앱 버전 비교: 점으로 나눈 숫자를 앞에서부터 비교하고, 읽을 수 없는 값은 막지 않는다
class AppVersionsTest {

    @Test
    void isBelow_comparesNumericPartsNotStrings() {
        assertThat(AppVersions.isBelow("1.9.0", "1.10.0")).isTrue();
        assertThat(AppVersions.isBelow("1.10.2", "1.9.0")).isFalse();
        assertThat(AppVersions.isBelow("2.0.0", "10.0.0")).isTrue();
    }

    @Test
    void isBelow_treatsMissingPartsAsZeroAndEqualAsNotBelow() {
        assertThat(AppVersions.isBelow("1.0", "1.0.0")).isFalse();
        assertThat(AppVersions.isBelow("1.0.0", "1.0.0")).isFalse();
        assertThat(AppVersions.isBelow("1", "1.0.1")).isTrue();
    }

    @Test
    void isBelow_isFalseWhenEitherSideCannotBeRead() {
        assertThat(AppVersions.isBelow("1.0.0-beta", "2.0.0")).isFalse();
        assertThat(AppVersions.isBelow("abc", "2.0.0")).isFalse();
        assertThat(AppVersions.isBelow("", "2.0.0")).isFalse();
        assertThat(AppVersions.isBelow(null, "2.0.0")).isFalse();
        assertThat(AppVersions.isBelow("1.0.0", "x")).isFalse();
        assertThat(AppVersions.isBelow("1.2.3.4.5", "9.0.0")).isFalse();
    }
}
