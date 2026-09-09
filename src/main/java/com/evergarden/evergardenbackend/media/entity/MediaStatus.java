package com.evergarden.evergardenbackend.media.entity;

/**
 * 업로드 진행 상태(ADR-023).
 *
 * <p>앱이 presigned URL로 스토리지에 직접 올리므로 서버는 두 시점에만 관여한다 —
 * URL을 발급할 때({@link #PENDING})와 완료를 통보받을 때({@link #READY}).
 */
public enum MediaStatus {

    /** URL만 발급된 상태. 아직 아카이브·타임캡슐에 담을 수 없다 */
    PENDING,

    /** 업로드가 끝나 다른 도메인에서 쓸 수 있는 상태 */
    READY
}
