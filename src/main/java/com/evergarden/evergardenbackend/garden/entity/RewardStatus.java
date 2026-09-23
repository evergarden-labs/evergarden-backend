package com.evergarden.evergardenbackend.garden.entity;

/** 지역 인증 한 건이 정원에 남긴 결과(GARDEN-02). */
public enum RewardStatus {

    /** 처음 방문해 오브젝트 해금 */
    UNLOCKED,

    /** 기존 오브젝트가 성장 */
    GROWN,

    /** 7일 쿨다운 안에 재인증이라 변화 없음(ADR-018) */
    COOLDOWN,

    /** 그 지역에 지정된 오브젝트가 없음 */
    NONE
}
