package com.evergarden.evergardenbackend.archive.entity;

/** 공동 편집 상태. 종료돼도 참여자는 조회할 수 있다(ADR-017). */
public enum CollaborationStatus {

    /** 혼자 만드는 중 */
    NONE,

    /** 공동 편집 중 */
    OPEN,

    /** 종료됨. 조회만 가능하고 편집은 막힌다 */
    CLOSED
}
