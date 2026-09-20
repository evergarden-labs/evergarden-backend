package com.evergarden.evergardenbackend.place.client.dto;

/** 법정동 코드·이름 한 쌍. 시/도든 시군구든 모양이 같아서 하나로 쓴다. */
public record RegionCode(String code, String name) {
}
