package com.evergarden.evergardenbackend.community.entity;

/** 게시물 상태. 삭제는 행을 지우지 않고 상태만 바꾼다(ADR-007). */
public enum PostStatus {
    ACTIVE,
    DELETED
}
