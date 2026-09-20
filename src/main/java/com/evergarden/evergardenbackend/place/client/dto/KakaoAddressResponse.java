package com.evergarden.evergardenbackend.place.client.dto;

import java.util.List;

/** 카카오 로컬 "주소로 좌표 변환" 응답. {@code x}=경도, {@code y}=위도, 둘 다 문자열로 온다. */
public record KakaoAddressResponse(List<Document> documents) {

    public record Document(String x, String y) {
    }
}
