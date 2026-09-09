package com.evergarden.evergardenbackend.community.entity;

/**
 * 게시물이 무엇을 공유했는지(COMM-04).
 *
 * <p>원본이 삭제되면 FK는 {@code null}이 되지만 이 값은 남는다.
 * 그래야 "처음부터 공유하지 않음"과 "공유했는데 삭제됨"을 구분할 수 있다(ADR-022).
 */
public enum ShareType {
    COURSE,
    ARCHIVE,
    BOTH
}
