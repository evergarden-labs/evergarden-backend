package com.evergarden.evergardenbackend.user.entity;

/**
 * 회원 상태. DB에는 문자열로 저장되고 {@code users_status_chk}가 값을 강제한다.
 *
 * <p>탈퇴는 행을 지우지 않고 {@link #WITHDRAWN}으로 바꾼다(ADR-007).
 * 게시물·댓글이 회원을 참조하고 있어 물리 삭제하면 남의 기록까지 사라진다.
 */
public enum UserStatus {

    /** 정상 이용 중 */
    ACTIVE,

    /** 경고를 받았지만 이용은 가능 */
    WARNED,

    /** 차단됨. 로그인 자체가 막힌다(AUTH-06) */
    BLOCKED,

    /** 탈퇴함. 30일 안에는 복구할 수 있다(ADR-054) */
    WITHDRAWN
}
