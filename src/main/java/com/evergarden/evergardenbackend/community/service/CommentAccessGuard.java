package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 댓글·대댓글 작성자 판별. 같은 저장소를 쓰므로 대댓글에도 그대로 쓴다
 * ({@code PostAccessGuard}와 같은 패턴).
 */
@Component
public class CommentAccessGuard {

    public void checkOwner(Comment comment, Long userId) {
        if (!comment.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }
}
