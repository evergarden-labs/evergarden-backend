package com.evergarden.evergardenbackend.report.entity;

/** 신고 대상 종류. 대댓글도 댓글과 같은 테이블이라 {@link #COMMENT}로 묶인다. */
public enum ReportTargetType {
    POST,
    COMMENT
}
