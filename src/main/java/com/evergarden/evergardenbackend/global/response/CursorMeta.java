package com.evergarden.evergardenbackend.global.response;

/**
 * 커서 방식 목록의 이어보기 정보(ADR-011).
 *
 * <p>아카이브 목록·타임캡슐 목록·커뮤니티 피드처럼 무한 스크롤하는 화면에 쓴다.
 * 번호 방식을 쓰면 목록이 갱신될 때 항목이 중복되거나 누락된다.
 *
 * @param nextCursor 다음 요청의 {@code cursor}로 그대로 넣는 값.
 *                   서버가 만든 불투명한 문자열이라 클라이언트가 해석하지 않는다
 */
public record CursorMeta(
        String nextCursor,
        boolean hasNext) implements ResponseMeta {

    public static CursorMeta of(String nextCursor) {
        return new CursorMeta(nextCursor, nextCursor != null);
    }

    public static CursorMeta last() {
        return new CursorMeta(null, false);
    }
}
