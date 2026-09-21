package com.evergarden.evergardenbackend.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 댓글 작성(COMM-11)의 검증 순서를 확인한다. */
class CommentServiceTest {

    private static final Long USER_ID = 1L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CommentMapper commentMapper = new CommentMapper();

    private final CommentService commentService =
            new CommentService(postRepository, commentRepository, userRepository, commentMapper);

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
}
