package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.dto.Reply;
import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.CommentStatus;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.CursorMeta;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.util.CursorCodec;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    /** 게시물의 최상위 댓글을 작성 순서로(COMM-03). 대댓글은 앞의 몇 개만 미리 담긴다. */
    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> listComments(Long postId, String cursor, int size) {
        Post post = findActivePost(postId);
        Long cursorId = CursorCodec.decode(cursor);
        List<Comment> fetched = commentRepository.findTopLevelAfter(post, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = fetched.size() > size;
        List<Comment> page = hasNext ? fetched.subList(0, size) : fetched;
        List<CommentResponse> responses = page.stream().map(this::toResponse).toList();

        return new CursorPage<>(responses, nextCursor(page, hasNext));
    }

    /**
     * 댓글의 대댓글을 작성 순서로(COMM-03). 부모 댓글이 삭제됐어도 대댓글은 그대로 보여야
     * 해서(ADR-007) {@code findActiveComment}가 아니라 존재 여부만 확인한다.
     */
    @Transactional(readOnly = true)
    public CursorPage<Reply> listReplies(Long parentCommentId, String cursor, int size) {
        Comment parent = findComment(parentCommentId);
        Long cursorId = CursorCodec.decode(cursor);
        List<Comment> fetched = commentRepository.findRepliesAfter(parent, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = fetched.size() > size;
        List<Comment> page = hasNext ? fetched.subList(0, size) : fetched;
        List<Reply> replies = page.stream().map(commentMapper::toReply).toList();

        return new CursorPage<>(replies, nextCursor(page, hasNext));
    }

    private CursorMeta nextCursor(List<Comment> page, boolean hasNext) {
        return hasNext ? CursorMeta.of(CursorCodec.encode(page.get(page.size() - 1).getId())) : CursorMeta.last();
    }

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
        return toResponse(updateContent(userId, commentId, request));
    }

    /**
     * 댓글을 지운다(COMM-13). 행을 지우지 않고 {@code status}만 바꾸고 내용을 비운다(ADR-007) —
     * 대댓글이 달려 있으면 자리를 남겨야 그 대댓글들이 안 사라진다. 화면에는 "삭제된
     * 댓글입니다"로 계속 보이므로, 게시물의 {@code commentCount}는 건드리지 않는다.
     */
    public void delete(Long userId, Long commentId) {
        deactivate(userId, commentId);
    }

    /**
     * 댓글에 답글을 단다(COMM-14). 깊이는 1단계까지다 — 부모가 이미 대댓글이면
     * {@code INVALID_REQUEST}다({@link Comment#replyTo}의 제약을 여기서 실제로 막는다).
     */
    public Reply createReply(Long userId, Long parentCommentId, CommentWriteRequest request) {
        Comment parent = findActiveComment(parentCommentId);
        if (parent.isReply()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Comment reply = Comment.replyTo(parent, userRepository.getReferenceById(userId), request.content());
        commentRepository.save(reply);
        reply.getPost().increaseCommentCount();

        return commentMapper.toReply(reply);
    }

    /** 대댓글 본문을 고친다(COMM-15). 저장소·검증 로직은 댓글과 같다. */
    public Reply updateReply(Long userId, Long replyId, CommentWriteRequest request) {
        return commentMapper.toReply(updateContent(userId, replyId, request));
    }

    /**
     * 대댓글을 지운다(COMM-16). 댓글과 달리 목록에서 완전히 빠진다 — 아래에 달릴 것이
     * 없어 자리를 남길 이유가 없어서다. 화면에서 사라지는 만큼, 게시물의
     * {@code commentCount}(대댓글 포함 수)도 같이 줄인다.
     */
    public void deleteReply(Long userId, Long replyId) {
        Comment reply = deactivate(userId, replyId);
        reply.getPost().decreaseCommentCount();
    }

    private Comment updateContent(Long userId, Long commentId, CommentWriteRequest request) {
        Comment comment = findActiveComment(commentId);
        commentAccessGuard.checkOwner(comment, userId);
        comment.updateContent(request.content());
        return comment;
    }

    private Comment deactivate(Long userId, Long commentId) {
        Comment comment = findActiveComment(commentId);
        commentAccessGuard.checkOwner(comment, userId);
        comment.delete();
        return comment;
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

    /** 삭제 여부를 안 가린다 — {@code listReplies}처럼 삭제된 댓글도 존재만 하면 되는 곳에 쓴다. */
    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private CommentResponse toResponse(Comment comment) {
        int replyCount = (int) commentRepository.countByParentAndStatus(comment, CommentStatus.ACTIVE);
        List<Reply> repliesPreview = commentRepository
                .findTop3ByParentAndStatusOrderByIdAsc(comment, CommentStatus.ACTIVE).stream()
                .map(commentMapper::toReply).toList();
        return commentMapper.toResponse(comment, replyCount, repliesPreview);
    }
}
