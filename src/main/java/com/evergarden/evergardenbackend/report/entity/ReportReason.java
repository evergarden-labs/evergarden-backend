package com.evergarden.evergardenbackend.report.entity;

/**
 * 신고 사유(ADR-046).
 *
 * <p>자유 입력만 받으면 관리자가 한 건씩 읽어야 하고 무의미한 신고가 섞인다.
 * 항목으로 받으면 유형별로 묶어 처리할 수 있고 급한 것부터 볼 수 있다.
 */
public enum ReportReason {
    OBSCENE,
    ABUSE,
    SPAM,
    FALSE_INFO,

    /** 기타. 이때는 설명이 필수다 — "기타"만으로는 판정할 수 없다 */
    ETC
}
