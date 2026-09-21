package com.evergarden.evergardenbackend.user.dto;

/** 명세의 {@code PublicProfile} 스키마. 내 프로필({@code MyProfile})보다 좁게 보인다. */
public record PublicProfile(Long userId, String nickname, String profileImageUrl, int postCount) {
}
