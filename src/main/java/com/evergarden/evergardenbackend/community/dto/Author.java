package com.evergarden.evergardenbackend.community.dto;

import com.evergarden.evergardenbackend.user.entity.User;

/** 명세의 {@code Author} 스키마. */
public record Author(Long userId, String nickname, String profileImageUrl) {

    public static Author of(User user) {
        return new Author(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
