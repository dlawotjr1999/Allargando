package com.wangnu.allargando.application.dto;

import com.wangnu.allargando.user.dto.CareerDTO;
import com.wangnu.allargando.user.entity.User;
import com.wangnu.allargando.application.entity.Application;
import com.wangnu.allargando.application.entity.ApplicationStatus;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 지원서 응답 DTO — 전 엔드포인트 공통(글 요약 post + 지원자 요약 applicant 중첩)
 * 모집자는 applicant를, 지원자는 post를 소비 (관점별 비대칭 없이 단일 DTO로 통일)
 */
@Getter
@Builder
public class AppResponseDTO {
    private Long id;
    private ApplicationPostSummaryDTO post;
    // UserResponseDTO 대신 ApplicantResponseDTO 사용: 모집자에게 지원자의 email이 노출되는 것을 차단
    private ApplicantResponseDTO applicant;
    // 지원자가 이 글에 지원하며 고른 악기(D9). applicant.instrument는 프로필 악기라 서로 다를 수 있어,
    // 모집자가 수락할 때 어느 악기 정원이 차는지 알 수 있도록 따로 내려 준다
    private String instrument;
    private String additionalInfo;
    private ApplicationStatus status;
    private LocalDateTime createdAt;

    // Application 엔티티 + 지원자 → 응답 DTO 변환 (글·지원자 요약 중첩)
    public static AppResponseDTO from(Application application, User user) {
        return from(application, user, null);
    }

    // Application 엔티티 + 지원자 + 배치 조회된 careers → 응답 DTO 변환
    // 목록 조회(지원자 목록/내 지원 목록) 전용 — N+1 방지를 위해 careers를 미리 배치 조회해 전달(BACKLOG.md #21)
    public static AppResponseDTO from(Application application, User user, List<CareerDTO> careers) {
        // 종료된 지원(거절·취소·철회)은 전화번호를 마스킹한다(D13). 지원자 본인의 "내 지원" 목록에도 같은 규칙이 적용되지만
        // 그 화면은 전화번호를 쓰지 않는다
        boolean maskPhone = !application.getStatus().exposesApplicantPhone();
        ApplicantResponseDTO applicant = ApplicantResponseDTO.from(user,
                careers != null ? careers : careersOf(user), maskPhone);
        return AppResponseDTO.builder()
                .id(application.getId())
                .post(ApplicationPostSummaryDTO.from(application.getPost()))
                .applicant(applicant)
                .instrument(application.getInstrument())
                .additionalInfo(application.getAdditionalInfo())
                .status(application.getStatus())
                .createdAt(application.getCreatedAt())
                .build();
    }

    // 단건 조회 전용 — user.getCareers() LAZY 접근(목록 조회는 배치로 미리 채운 careers를 쓴다)
    private static List<CareerDTO> careersOf(User user) {
        return user.getCareers().stream().map(CareerDTO::from).collect(java.util.stream.Collectors.toList());
    }
}
