package com.evergarden.evergardenbackend.global.response;

/**
 * 성공 응답의 {@code meta} 자리에 들어가는 값.
 *
 * <p>목록이 아니면 {@code null}이다. 목록이면 페이지 나누는 방식에 따라
 * {@link PageMeta} 또는 {@link CursorMeta}가 온다(ADR-011).
 */
public interface ResponseMeta {
}
