package com.evergarden.evergardenbackend.global.security;

/**
 * 토큰 용도. {@code type} 클레임에 담는다.
 *
 * <p>구분하지 않으면 리프레시 토큰을 {@code Authorization} 헤더에 넣어 API를 부를 수 있다.
 * 리프레시는 7일짜리라 액세스 토큰의 30분 만료가 사실상 무력해진다.
 */
public enum TokenType {

    /** API 호출용. 30분. */
    ACCESS,

    /** 재발급용. 7일. {@code POST /auth/token/refresh}의 <b>본문</b>으로만 오간다(쿠키 안 씀). */
    REFRESH
}
