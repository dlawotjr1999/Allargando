package com.wangnu.allargando.report.entity;

// 신고 처리 상태 — 접수되면 PENDING, 운영자가 확인·조치한 뒤 DB에서 RESOLVED로 바꾼다(관리자 화면 없음)
public enum ReportStatus {
    PENDING, RESOLVED
}
