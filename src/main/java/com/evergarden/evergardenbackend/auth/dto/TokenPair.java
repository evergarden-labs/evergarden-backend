package com.evergarden.evergardenbackend.auth.dto;

/** 명세의 {@code TokenPair} 스키마. {@code refreshToken}(AUTH-05)이 쓴다. */
public record TokenPair(String accessToken, String refreshToken) {
}
