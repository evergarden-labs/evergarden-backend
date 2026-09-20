package com.evergarden.evergardenbackend.place.client.dto;

/**
 * {@code detailIntro2} 응답 항목. 이용시간·휴무일 필드명이 콘텐츠타입마다 다르다 —
 * 실측 결과 관광지(12)·문화시설(14)·음식점(39) 세 타입만 값이 신뢰할 수 있게 채워져
 * 있어(다른 프로젝트의 실측 확인, PR 참고는 docs/place-data-sync.md) 이 세 타입의
 * 필드명만 받는다. 나머지 타입은 이 필드들이 전부 비어 온다 — 억지로 다른 개념의
 * 필드(체크인시간, 행사기간 등)를 끼워 맞추지 않는다.
 */
public record DetailIntroItem(
        String contentid,
        String usetime,
        String restdate,
        String usetimeculture,
        String restdateculture,
        String opentimefood,
        String restdatefood) {

    /** 관광지=usetime, 문화시설=usetimeculture, 음식점=opentimefood 중 값이 있는 것. */
    public String resolveUseTime() {
        return firstNonBlank(usetime, usetimeculture, opentimefood);
    }

    /** 관광지=restdate, 문화시설=restdateculture, 음식점=restdatefood 중 값이 있는 것. */
    public String resolveRestDate() {
        return firstNonBlank(restdate, restdateculture, restdatefood);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
