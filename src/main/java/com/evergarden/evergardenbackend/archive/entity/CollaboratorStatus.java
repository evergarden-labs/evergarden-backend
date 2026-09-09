package com.evergarden.evergardenbackend.archive.entity;

/** 초대 수락 상태. {@link #LEFT}는 스스로 나갔거나 탈퇴로 빠진 경우다. */
public enum CollaboratorStatus {
    INVITED,
    JOINED,
    LEFT
}
