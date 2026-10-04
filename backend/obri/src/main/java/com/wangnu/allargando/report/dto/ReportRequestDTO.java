package com.wangnu.allargando.report.dto;

import com.wangnu.allargando.report.entity.ReportReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 신고 요청 바디 (POST /api/reports/posts/{postId}, /api/reports/users/{nickname})
 * 신고 대상은 경로로 지정하므로 바디엔 사유와 선택 설명만 둔다
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequestDTO {

    @NotNull(message = "신고 사유를 선택해주세요")
    private ReportReason reason;

    @Size(max = 500, message = "설명은 500자 이내로 입력해주세요")
    private String detail;
}
