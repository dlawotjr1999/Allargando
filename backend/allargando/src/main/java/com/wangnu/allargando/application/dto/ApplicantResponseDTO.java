package com.wangnu.allargando.application.dto;

import com.wangnu.allargando.user.dto.CareerDTO;
import com.wangnu.allargando.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

// 지원서 응답 내 지원자 공개 프로필 DTO
// UserResponseDTO를 그대로 쓰면 모집자 조회 시 지원자의 email까지 노출되므로 별도 분리
// phoneNumber는 지원 시점부터 모집자에게 공개(공개 프로필과는 다른 정책) — Application 명세 4.2 참고.
// 단 거절·취소·철회로 끝난 지원은 마스킹해 내려 준다(D13)
@Getter
@Builder
public class ApplicantResponseDTO {
    private String name;
    private String nickname;
    private String instrument;
    private String phoneNumber;
    private List<CareerDTO> careers;

    // User 엔티티 → 지원자 프로필 DTO 변환 (email 제외, phoneNumber 포함)
    // 단건 조회 전용 — user.getCareers() LAZY 접근(BACKLOG.md #21 대상 아님, 목록 조회는 아래 배치 오버로드 사용)
    public static ApplicantResponseDTO from(User user) {
        return from(user, user.getCareers().stream()
                .map(CareerDTO::from)
                .collect(Collectors.toList()));
    }

    // User 엔티티 + 배치 조회된 careers → 지원자 프로필 DTO 변환
    // 목록 조회(지원자 목록/내 지원 목록) 전용 — user.getCareers() lazy 접근을 피해 N+1을 방지(BACKLOG.md #21)
    public static ApplicantResponseDTO from(User user, List<CareerDTO> careers) {
        return from(user, careers, false);
    }

    // maskPhone이 true면 전화번호를 마스킹해 내려 준다(종료된 지원, D13)
    public static ApplicantResponseDTO from(User user, List<CareerDTO> careers, boolean maskPhone) {
        return ApplicantResponseDTO.builder()
                .name(maskPhone ? maskName(user.getName()) : user.getName())
                .nickname(user.getNickname())
                .instrument(user.getInstrument())
                .phoneNumber(maskPhone ? maskPhoneNumber(user.getPhoneNumber()) : user.getPhoneNumber())
                .careers(careers)
                .build();
    }

    // 앞 3자와 뒤 4자만 남기고 사이의 숫자를 *로 가린다(010-1234-5678 → 010-****-5678, +821012345678 → +82*****5678).
    // 7자 이하의 짧은 값은 숫자를 전부 가린다. 하이픈 같은 구분자는 그대로 둔다
    // 종료된 지원의 이름은 전화번호처럼 가린다 — 첫 글자만 남긴다(홍길동 → 홍**)
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1) + "*".repeat(name.length() - 1);
    }

    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int length = phoneNumber.length();
        int keepHead = length > 7 ? 3 : 0;
        int keepTail = length > 7 ? 4 : 0;
        StringBuilder masked = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = phoneNumber.charAt(i);
            boolean kept = i < keepHead || i >= length - keepTail;
            masked.append(!kept && Character.isDigit(c) ? '*' : c);
        }
        return masked.toString();
    }
}
