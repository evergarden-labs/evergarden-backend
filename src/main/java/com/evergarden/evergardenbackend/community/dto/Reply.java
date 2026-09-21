package com.evergarden.evergardenbackend.community.dto;

import com.evergarden.evergardenbackend.community.entity.CommentStatus;
import java.time.LocalDateTime;

/** 명세의 {@code Reply} 스키마. */
public record Reply(
        Long replyId,
        Long parentCommentId,
        Author author,
        String content,
        CommentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
