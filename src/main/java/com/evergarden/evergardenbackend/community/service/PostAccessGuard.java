package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 게시물 작성자 판별. 트립과 마찬가지로 공동 편집 개념이 없어 작성자 확인 하나뿐이지만,
 * {@code updatePost}·{@code deletePost} 둘 다 쓰게 될 거라 한곳에 모은다.
 */
@Component
public class PostAccessGuard {

    public void checkOwner(Post post, Long userId) {
        if (!post.isWrittenBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }
}
