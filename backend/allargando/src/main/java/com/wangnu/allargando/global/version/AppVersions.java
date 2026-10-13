package com.wangnu.allargando.global.version;

/*
 * 앱 버전 문자열 비교 — 점으로 나눈 숫자를 앞에서부터 비교한다("1.10.2" > "1.9.0", "1.0" == "1.0.0").
 * 숫자로 읽을 수 없는 값("1.0.0-beta", "abc", 빈 값)은 비교하지 않고 "낮지 않다"로 본다.
 * 모르는 형식 때문에 사용자를 막는 것보다 통과시키는 쪽이 안전하기 때문이다.
 */
public final class AppVersions {

    private AppVersions() {
    }

    // version이 minimum보다 낮으면 true. 둘 중 하나라도 숫자 버전으로 읽을 수 없으면 false
    public static boolean isBelow(String version, String minimum) {
        int[] v = parse(version);
        int[] m = parse(minimum);
        if (v == null || m == null) {
            return false;
        }
        int length = Math.max(v.length, m.length);
        for (int i = 0; i < length; i++) {
            int a = i < v.length ? v[i] : 0;
            int b = i < m.length ? m[i] : 0;
            if (a != b) {
                return a < b;
            }
        }
        return false;
    }

    // "1.10.2" → [1, 10, 2]. 숫자 조각이 아니거나 4조각을 넘거나 비어 있으면 null
    static int[] parse(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("\\.", -1);
        if (parts.length > 4) {
            return null;
        }
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("\\d{1,6}")) {
                return null;
            }
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }
}
