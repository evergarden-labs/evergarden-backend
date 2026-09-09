package com.evergarden.evergardenbackend.report.entity;

/** 제재 종류. 해제도 이력으로 남겨 누가 왜 풀었는지 추적한다(ADMIN-11). */
public enum SanctionType {
    WARNING,
    BLOCK,
    UNBLOCK
}
