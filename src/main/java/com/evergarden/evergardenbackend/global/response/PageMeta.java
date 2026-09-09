package com.evergarden.evergardenbackend.global.response;

import org.springframework.data.domain.Page;

/**
 * 번호 방식 목록의 페이지 정보(ADR-011).
 *
 * <p>일정 목록·좋아요 목록·알림 목록·관리자 화면에 쓴다.
 * 무한 스크롤 화면은 {@link CursorMeta}를 쓴다.
 */
public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages) implements ResponseMeta {

    /** Spring Data의 0부터 시작하는 페이지 번호를 명세의 1부터로 바꾼다. */
    public static PageMeta from(Page<?> page) {
        return new PageMeta(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
