package com.evergarden.evergardenbackend.notification.entity;

/**
 * 알림 종류.
 *
 * <p>차단은 여기 없다. 차단된 사용자는 로그인 자체가 막혀 알림 목록에 도달할 수 없어,
 * 로그인 응답으로 안내한다(AUTH-06).
 */
public enum NotificationType {

    /** 공동 편집 초대(NOTI-02) */
    COLLAB_INVITE,

    /** 타임캡슐 해제(TC-04) */
    CAPSULE_UNLOCK,

    /** 경고(NOTI-05). 운영 통지라 수신을 끌 수 없다(ADR-047) */
    WARNING;

    /** 사용자가 수신을 끌 수 있는 종류인지. */
    public boolean isMutable() {
        return this != WARNING;
    }
}
