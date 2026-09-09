package com.evergarden.evergardenbackend.report.entity;

/** 제재를 누가 걸었는지. */
public enum SanctionSource {

    /** 관리자가 재량으로 즉시 제재(ADMIN-04 · ADMIN-05) */
    MANUAL,

    /** 유효 신고 누적이 기준에 닿아 시스템이 자동으로 처리(ADMIN-10) */
    AUTO
}
