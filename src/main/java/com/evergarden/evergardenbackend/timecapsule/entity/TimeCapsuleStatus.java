package com.evergarden.evergardenbackend.timecapsule.entity;

/** 타임캡슐 상태. 봉인된 캡슐의 내용은 API로 나가지 않는다. */
public enum TimeCapsuleStatus {

    /** 조건 미충족. 본인도 내용을 볼 수 없다 */
    SEALED,

    /** 조건이 충족돼 열 수 있는 상태 */
    UNLOCKABLE,

    /** 열어봤다. 이때부터 내용이 조회된다 */
    OPENED
}
