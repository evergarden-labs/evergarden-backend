package com.evergarden.evergardenbackend.community.dto;

/** 명세의 {@code LikeResult} 스키마. {@code likePost}·{@code unlikePost} 공통 응답. */
public record LikeResult(Long postId, int likeCount, boolean likedByMe) {
}
