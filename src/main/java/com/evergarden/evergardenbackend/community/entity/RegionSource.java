package com.evergarden.evergardenbackend.community.entity;

/** 게시물 지역이 어디서 왔는지(ADR-003). */
public enum RegionSource {

    /** 공유한 코스의 여행지와 장소들에서 자동 추출 */
    COURSE,

    /** 코스 없이 아카이브만 공유해 작성자가 직접 고름 */
    MANUAL
}
