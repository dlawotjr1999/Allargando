package com.wangnu.allargando.application.dto;

import com.wangnu.allargando.application.entity.Application;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/*
 * 지원서 제출 요청 바디 (POST /api/applications/submit)
 * 지원자 프로필(악기·약력)은 토큰으로 조회하고 여기서는 대상 글·어필 문구만 받음
 */
@Getter
@Builder
public class AppRequestDTO {
    // 누락 시 findById(null) → 500이 나던 것을 사전 400으로 차단
    @NotNull(message = "모집글 ID를 입력해주세요")
    private Long postId;

    // 지원자가 고른 악기(D9). 보내지 않으면 프로필 악기로 대신한다 — 프로필 악기를 기본값으로 두는 것이 설계 의도라
    // 필수로 바꾸지 않는다(앱은 지원 모달에서 프로필 악기를 미리 선택해 항상 보낸다)
    @Size(max = 255, message = "악기는 255자 이내여야 합니다")
    private String instrument;

    @Size(max = 255, message = "어필 문구는 255자 이내여야 합니다")
    private String additionalInfo;

    // 개인정보 제3자 제공 동의(필수) — 지원자가 동의 화면에서 체크했는지. true가 아니면 서비스가 400으로 거절한다
    private Boolean agreedToThirdPartyProvision;

    // 동의 화면의 문안 버전. Application.CONSENT_VERSION과 같아야 한다(다르면 400)
    @Size(max = 20, message = "동의 문안 버전이 올바르지 않습니다")
    private String consentVersion;

    // 필드로부터 요청 DTO 생성 (주로 테스트 편의용) — 현재 문안에 동의한 요청을 만든다
    public static AppRequestDTO from(Long postId, String additionalInfo) {
        return AppRequestDTO.builder()
                .postId(postId)
                .additionalInfo(additionalInfo)
                .agreedToThirdPartyProvision(true)
                .consentVersion(Application.CONSENT_VERSION)
                .build();
    }
}
