package com.evergarden.evergardenbackend.community.entity;

/** 댓글 상태. 대댓글이 달린 댓글은 삭제해도 자리를 남긴다(ADR-007). */
public enum CommentStatus {
    ACTIVE,
    DELETED
}
