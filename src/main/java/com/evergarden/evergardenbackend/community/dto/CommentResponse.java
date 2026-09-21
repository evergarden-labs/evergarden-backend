package com.evergarden.evergardenbackend.community.dto;

import com.evergarden.evergardenbackend.community.entity.CommentStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 명세의 {@code Comment} 스키마. 엔티티 {@code Comment}와 이름이 겹쳐 Response를 붙인다. */
public record CommentResponse(
        Long commentId,
        Author author,
        String content,
        CommentStatus status,
        int replyCount,
        List<Reply> replies,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
