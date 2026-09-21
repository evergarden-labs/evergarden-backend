package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.community.dto.Author;
import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.Reply;
import com.evergarden.evergardenbackend.community.entity.Comment;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link Comment} 응답 변환. 댓글은 {@link CommentResponse}, 대댓글은 {@link Reply}. */
@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment comment, int replyCount, List<Reply> repliesPreview) {
        return new CommentResponse(
                comment.getId(), Author.of(comment.getAuthor()), comment.getContent(), comment.getStatus(),
                replyCount, repliesPreview, comment.getCreatedAt(), comment.getUpdatedAt());
    }

    public Reply toReply(Comment reply) {
        return new Reply(
                reply.getId(), reply.getParent().getId(), Author.of(reply.getAuthor()), reply.getContent(),
                reply.getStatus(), reply.getCreatedAt(), reply.getUpdatedAt());
    }
}
