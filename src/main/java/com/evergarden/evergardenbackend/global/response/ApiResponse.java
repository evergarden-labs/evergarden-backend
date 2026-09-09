package com.evergarden.evergardenbackend.global.response;

/**
 * 모든 성공 응답의 봉투(ADR-008).
 *
 * <pre>{@code { "data": ..., "meta": ... }}</pre>
 *
 * <p>본문이 없는 응답도 {@code 204}를 쓰지 않고 {@code 200}에 {@code data: null}을 담는다(ADR-009).
 * 클라이언트가 응답 처리 분기를 하나로 유지할 수 있게 하기 위해서다.
 *
 * <p>실패 응답은 이 봉투를 쓰지 않는다. {@link com.evergarden.evergardenbackend.global.response.ErrorResponse}가
 * {@code error} 하나만 담아 필드가 겹치지 않는다.
 */
public record ApiResponse<T>(T data, ResponseMeta meta) {

    /** 단건 응답. */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    /** 목록 응답. */
    public static <T> ApiResponse<T> of(T data, ResponseMeta meta) {
        return new ApiResponse<>(data, meta);
    }

    /** 돌려줄 것이 없는 성공. 삭제·로그아웃 같은 오퍼레이션이 쓴다. */
    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(null, null);
    }
}
