package com.evergarden.evergardenbackend.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.dto.Reply;
import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 댓글 작성·수정·삭제(COMM-11·12·13)의 검증 순서를 확인한다. */
class CommentServiceTest {

    private static final Long USER_ID = 1L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CommentAccessGuard commentAccessGuard = mock(CommentAccessGuard.class);
    private final CommentMapper commentMapper = new CommentMapper();

    private final CommentService commentService = new CommentService(
            postRepository, commentRepository, userRepository, commentAccessGuard, commentMapper);

    private User author;

    @BeforeEach
    void setUp() {
        author = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(author, "id", USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(author);
    }

    private Post post(Long id) {
        Post post = Post.builder().author(author).content("게시물 내용").shareType(ShareType.ARCHIVE).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private Comment comment(Long id) {
        Comment comment = Comment.on(post(5L), author, "원래 댓글");
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    @Test
    @DisplayName("없는 게시물에 댓글을 달면 POST_NOT_FOUND")
    void 없는_게시물() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());
        CommentWriteRequest request = new CommentWriteRequest("좋은 코스네요");

        assertThatThrownBy(() -> commentService.create(USER_ID, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("삭제된 게시물에 댓글을 달면 POST_NOT_FOUND")
    void 삭제된_게시물() {
        Post post = post(5L);
        post.delete();
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        CommentWriteRequest request = new CommentWriteRequest("좋은 코스네요");

        assertThatThrownBy(() -> commentService.create(USER_ID, 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 작성은 댓글 수를 늘리고 새 댓글을 돌려준다")
    void 정상_작성() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        CommentWriteRequest request = new CommentWriteRequest("좋은 코스네요");

        CommentResponse result = commentService.create(USER_ID, 5L, request);

        assertThat(result.content()).isEqualTo("좋은 코스네요");
        assertThat(result.author().userId()).isEqualTo(USER_ID);
        assertThat(result.replyCount()).isZero();
        assertThat(result.replies()).isEmpty();
        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    // ── 수정(COMM-12) ────────────────────────────────────────

    @Test
    @DisplayName("없는 댓글을 수정하면 COMMENT_NOT_FOUND")
    void 수정_없는댓글() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());
        CommentWriteRequest request = new CommentWriteRequest("고친 내용");

        assertThatThrownBy(() -> commentService.update(USER_ID, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 댓글을 수정하면 COMMENT_NOT_FOUND")
    void 수정_삭제된댓글() {
        Comment comment = comment(10L);
        comment.delete();
        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        CommentWriteRequest request = new CommentWriteRequest("고친 내용");

        assertThatThrownBy(() -> commentService.update(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 댓글을 수정하면 403 — 댓글 접근가드에 위임한다")
    void 수정_남의댓글() {
        Comment comment = comment(10L);
        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(commentAccessGuard).checkOwner(comment, USER_ID);
        CommentWriteRequest request = new CommentWriteRequest("고친 내용");

        assertThatThrownBy(() -> commentService.update(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("정상 수정은 본문을 바꾸고 대댓글 수·미리보기를 함께 돌려준다")
    void 수정_정상() {
        Comment comment = comment(10L);
        Comment reply = Comment.replyTo(comment, author, "답글");
        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        given(commentRepository.countByParent(comment)).willReturn(1L);
        given(commentRepository.findTop3ByParentOrderByIdAsc(comment)).willReturn(List.of(reply));
        CommentWriteRequest request = new CommentWriteRequest("고친 내용");

        CommentResponse result = commentService.update(USER_ID, 10L, request);

        assertThat(result.content()).isEqualTo("고친 내용");
        assertThat(result.replyCount()).isEqualTo(1);
        assertThat(result.replies()).hasSize(1);
    }

    // ── 삭제(COMM-13) ────────────────────────────────────────

    @Test
    @DisplayName("없는 댓글을 삭제하면 COMMENT_NOT_FOUND")
    void 삭제_없는댓글() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 댓글을 삭제하면 403 — 댓글 접근가드에 위임한다")
    void 삭제_남의댓글() {
        Comment comment = comment(10L);
        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(commentAccessGuard).checkOwner(comment, USER_ID);

        assertThatThrownBy(() -> commentService.delete(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("정상 삭제는 행을 지우지 않고 상태만 바꾼다 — 게시물 댓글 수는 그대로다")
    void 삭제_정상() {
        Comment comment = comment(10L);
        Post post = comment.getPost();
        post.increaseCommentCount();
        given(commentRepository.findById(10L)).willReturn(Optional.of(comment));

        commentService.delete(USER_ID, 10L);

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getContent()).isNull();
        assertThat(post.getCommentCount()).isEqualTo(1);
        verify(commentRepository, never()).delete(any());
    }

    // ── 대댓글 작성(COMM-14) ─────────────────────────────────

    @Test
    @DisplayName("없는 댓글에 답글을 달면 COMMENT_NOT_FOUND")
    void 답글작성_없는댓글() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());
        CommentWriteRequest request = new CommentWriteRequest("답글");

        assertThatThrownBy(() -> commentService.createReply(USER_ID, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("대댓글에 또 대댓글을 달면 INVALID_REQUEST — 깊이는 1단계까지다")
    void 답글작성_깊이제한() {
        Comment parentComment = comment(10L);
        Comment reply = Comment.replyTo(parentComment, author, "답글");
        ReflectionTestUtils.setField(reply, "id", 11L);
        given(commentRepository.findById(11L)).willReturn(Optional.of(reply));
        CommentWriteRequest request = new CommentWriteRequest("대대댓글");

        assertThatThrownBy(() -> commentService.createReply(USER_ID, 11L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 답글 작성은 부모 댓글의 게시물 댓글 수를 늘린다")
    void 답글작성_정상() {
        Comment parentComment = comment(10L);
        Post post = parentComment.getPost();
        given(commentRepository.findById(10L)).willReturn(Optional.of(parentComment));
        CommentWriteRequest request = new CommentWriteRequest("좋은 답글");

        Reply result = commentService.createReply(USER_ID, 10L, request);

        assertThat(result.content()).isEqualTo("좋은 답글");
        assertThat(result.parentCommentId()).isEqualTo(10L);
        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    // ── 대댓글 수정·삭제(COMM-15·16) ─────────────────────────

    @Test
    @DisplayName("남의 답글을 수정하면 403 — 댓글 접근가드에 위임한다(댓글과 같은 저장소)")
    void 답글수정_남의것() {
        Comment parentComment = comment(10L);
        Comment reply = Comment.replyTo(parentComment, author, "답글");
        ReflectionTestUtils.setField(reply, "id", 11L);
        given(commentRepository.findById(11L)).willReturn(Optional.of(reply));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(commentAccessGuard).checkOwner(reply, USER_ID);
        CommentWriteRequest request = new CommentWriteRequest("고친 답글");

        assertThatThrownBy(() -> commentService.updateReply(USER_ID, 11L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("정상 답글 수정은 Reply 모양으로 돌려준다")
    void 답글수정_정상() {
        Comment parentComment = comment(10L);
        Comment reply = Comment.replyTo(parentComment, author, "답글");
        ReflectionTestUtils.setField(reply, "id", 11L);
        given(commentRepository.findById(11L)).willReturn(Optional.of(reply));
        CommentWriteRequest request = new CommentWriteRequest("고친 답글");

        Reply result = commentService.updateReply(USER_ID, 11L, request);

        assertThat(result.content()).isEqualTo("고친 답글");
        assertThat(result.parentCommentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("정상 답글 삭제는 상태만 바꾼다")
    void 답글삭제_정상() {
        Comment parentComment = comment(10L);
        Comment reply = Comment.replyTo(parentComment, author, "답글");
        ReflectionTestUtils.setField(reply, "id", 11L);
        given(commentRepository.findById(11L)).willReturn(Optional.of(reply));

        commentService.deleteReply(USER_ID, 11L);

        assertThat(reply.isDeleted()).isTrue();
        verify(commentRepository, never()).delete(any());
    }
}
