package com.evergarden.evergardenbackend.archive.entity;

/** 공동 편집자 역할. 소유자는 나갈 수 없고 종료하거나 삭제해야 한다(ARCH-13). */
public enum CollaboratorRole {
    OWNER,
    EDITOR
}
