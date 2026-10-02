package com.obri_back.obri.report.entity;

// 신고 사유 — 앱 신고 화면의 선택지와 1:1 (표시 문구는 프론트가 가진다)
public enum ReportReason {
    SPAM,          // 스팸·광고
    INAPPROPRIATE, // 부적절한 내용(음란·폭력 등)
    FRAUD,         // 사기·허위 정보
    HARASSMENT,    // 괴롭힘·혐오 표현
    OTHER          // 기타
}
