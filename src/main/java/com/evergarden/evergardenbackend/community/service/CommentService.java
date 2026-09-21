package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.dto.Reply;
import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 댓글·대댓글(COMM-11 이하). */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CommentAccessGuard commentAccessGuard;
    private final CommentMapper commentMapper;

    /** 게시물에 댓글을 단다(COMM-11). */
    public CommentResponse create(Long userId, Long postId, CommentWriteRequest request) {
        Post post = findActivePost(postId);

        Comment comment = Comment.on(post, userRepository.getReferenceById(userId), request.content());
        commentRepository.save(comment);
        post.increaseCommentCount();

        return commentMapper.toResponse(comment, 0, List.of());
    }

    /** 댓글 본문을 고친다(COMM-12). */
    public CommentResponse update(Long userId, Long commentId, CommentWriteRequest request) {
        Comment comment = findActiveComment(commentId);
        commentAccessGuard.checkOwner(comment, userId);

        comment.updateContent(request.content());

        return toResponse(comment);
    }

    /**
     * 댓글을 지운다(COMM-13). 행을 지우지 않고 {@code status}만 바꾸고 내용을 비운다(ADR-007) —
     * 대댓글이 달려 있으면 자리를 남겨야 그 대댓글들이 안 사라진다. 게시물의
     * {@code commentCount}는 대댓글까지 포함한 수라 여기서 건드리지 않는다.
     */
    public void delete(Long userId, Long commentId) {
        Comment comment = findActiveComment(commentId);
        commentAccessGuard.checkOwner(comment, userId);
        comment.delete();
    }

    private Post findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private Comment findActiveComment(Long commentId) {
        return commentRepository.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private CommentResponse toResponse(Comment comment) {
        int replyCount = (int) commentRepository.countByParent(comment);
        List<Reply> repliesPreview = commentRepository.findTop3ByParentOrderByIdAsc(comment).stream()
                .map(commentMapper::toReply).toList();
        return commentMapper.toResponse(comment, replyCount, repliesPreview);
    }
}
