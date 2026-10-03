package com.obri_back.obri.user.dto;

import com.obri_back.obri.user.entity.Career;
import com.obri_back.obri.user.entity.User;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/*
 * 경력 요청/응답 공용 DTO (id가 있으면 기존, 없으면 신규)
 * 단체명·설명은 둘 다 비어도 된다 — 혼자 연주해 온 사람처럼 소속이 없는 경우를 허용(길이만 DB 컬럼(255)에 맞춰 제한)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerDTO {
    private Long id;          // nullable, 없으면 새로 추가

    @Size(max = 255, message = "단체명은 255자 이내여야 합니다")
    private String organization;

    @Size(max = 255, message = "경력 설명은 255자 이내여야 합니다")
    private String contexts;

    // 단체명·설명이 모두 비어 있으면 저장할 내용이 없는 행 — 가입 화면의 기본 빈 입력행이 그대로 올라오는 경우를 걸러낸다
    // (is/get 접두사를 피한 이름: Jackson이 응답에 속성으로 노출하지 않도록)
    public boolean hasContent() {
        return !(isBlankText(organization) && isBlankText(contexts));
    }

    /*
     * 요청 경력 목록 → 엔티티 목록 (가입·내 정보 수정 공통)
     * 내용이 없는 행은 버리고, 앞뒤 공백을 지우며, 한쪽만 비었으면 빈 문자열로 저장해 응답에서 null이 나오지 않게 한다
     */
    public static List<Career> toEntities(User user, List<CareerDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .filter(CareerDTO::hasContent)
                .map(dto -> Career.of(user, cleanText(dto.organization), cleanText(dto.contexts)))
                .toList();
    }

    private static boolean isBlankText(String text) {
        return text == null || text.isBlank();
    }

    private static String cleanText(String text) {
        return text == null ? "" : text.strip();
    }

    // Career 엔티티 → DTO 변환
    public static CareerDTO from(Career career) {
        return CareerDTO.builder()
                .id(career.getId())
                .organization(career.getOrganization())
                .contexts(career.getContexts())
                .build();
    }
}
