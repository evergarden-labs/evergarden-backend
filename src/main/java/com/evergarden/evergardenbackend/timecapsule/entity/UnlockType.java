package com.evergarden.evergardenbackend.timecapsule.entity;

/**
 * 타임캡슐 해제 조건. 날짜와 위치 중 하나만 고른다(TC-01).
 *
 * <p>{@link #DATE}는 서버 배치가 매일 확인하고, {@link #LOCATION}은
 * 앱이 켜질 때 위치를 보고해야 판정된다(ADR-026).
 */
public enum UnlockType {
    DATE,
    LOCATION
}
