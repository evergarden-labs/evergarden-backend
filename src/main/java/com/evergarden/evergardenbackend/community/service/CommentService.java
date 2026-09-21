package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
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
    private final CommentMapper commentMapper;

    /** 게시물에 댓글을 단다(COMM-11). */
    public CommentResponse create(Long userId, Long postId, CommentWriteRequest request) {
        Post post = findActivePost(postId);

        Comment comment = Comment.on(post, userRepository.getReferenceById(userId), request.content());
        commentRepository.save(comment);
        post.increaseCommentCount();

        return commentMapper.toResponse(comment, 0, List.of());
    }

    private Post findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }
}
