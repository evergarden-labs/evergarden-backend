package com.evergarden.evergardenbackend.admin.service;

import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘텐츠 강제 삭제(ADMIN-06·07·08). 대댓글도 댓글과 같은 테이블이라
 * {@link #deleteComment}를 그대로 같이 쓴다 — 경로만 {@code /admin/comments}·
 * {@code /admin/replies}로 나뉘고 로직은 완전히 같다(스펙에 명시된 그대로).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AdminRepository adminRepository;

    /** 정책 위반 게시물을 운영자 권한으로 내린다(ADMIN-06). 작성자 본인 삭제와 달리 사유가 남는다. */
    @Transactional
    public void deletePost(Long adminId, Long postId, String reason) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Admin admin = adminRepository.getReferenceById(adminId);
        post.adminDelete(admin, reason);
    }

    /**
     * 정책 위반 댓글·대댓글을 운영자 권한으로 내린다(ADMIN-07·08). 대댓글이 달려 있어도
     * 자리는 남긴다(ADR-007, {@code Comment.delete()}와 같은 규칙).
     */
    @Transactional
    public void deleteComment(Long adminId, Long commentId, String reason) {
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        Admin admin = adminRepository.getReferenceById(adminId);
        comment.adminDelete(admin, reason);
    }
}
