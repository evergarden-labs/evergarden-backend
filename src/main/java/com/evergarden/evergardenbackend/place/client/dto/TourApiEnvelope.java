package com.evergarden.evergardenbackend.place.client.dto;

import java.util.List;

/**
 * TourAPI 응답 공통 봉투 — {@code response.body.items.item} 아래 실제 데이터가 온다.
 *
 * <p>결과가 없으면 {@code items}가 객체가 아니라 빈 문자열로 온다고 알려져 있다 —
 * 이 클라이언트가 지금 쓰는 {@code ldongCode2}는 결과가 없을 일이 없어 아직 그 경우를
 * 다루지 않는다. 결과가 자주 비는 오퍼레이션(검색·주변 추천)을 붙일 때 손봐야 한다.
 */
public record TourApiEnvelope<T>(Response<T> response) {

    public record Response<T>(Header header, Body<T> body) {
    }

    public record Header(String resultCode, String resultMsg) {
    }

    public record Body<T>(Items<T> items, int numOfRows, int pageNo, int totalCount) {
    }

    public record Items<T>(List<T> item) {
    }

    public List<T> items() {
        Items<T> items = response.body().items();
        return items == null || items.item() == null ? List.of() : items.item();
    }

    public int totalCount() {
        return response.body().totalCount();
    }
}
