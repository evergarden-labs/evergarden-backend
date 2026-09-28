package com.evergarden.evergardenbackend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 콘텐츠 강제 삭제(ADMIN-06·07·08)를 다룬다. */
class AdminContentServiceTest {

    private static final Long ADMIN_ID = 9L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final AdminContentService service =
            new AdminContentService(postRepository, commentRepository, adminRepository);

    private Admin admin;
    private User author;

    @BeforeEach
    void setUp() {
        admin = Admin.builder().loginId("ops").passwordHash("hash").name("담당자").build();
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        given(adminRepository.getReferenceById(ADMIN_ID)).willReturn(admin);
        author = User.builder().nickname("작성자").build();
        ReflectionTestUtils.setField(author, "id", 1L);
    }

    @Test
    @DisplayName("게시물을 강제 삭제하면 누가 왜 지웠는지 같이 남는다")
    void 게시물_삭제() {
        Post post = Post.builder().author(author).content("내용").shareType(ShareType.ARCHIVE).build();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        service.deletePost(ADMIN_ID, 1L, "정책 위반");

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedByAdmin()).isEqualTo(admin);
        assertThat(post.getDeleteReason()).isEqualTo("정책 위반");
    }

    @Test
    @DisplayName("이미 삭제된 게시물이면 POST_NOT_FOUND")
    void 게시물_이미삭제됨() {
        Post post = Post.builder().author(author).content("내용").shareType(ShareType.ARCHIVE).build();
        post.delete();
        given(postRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> service.deletePost(ADMIN_ID, 1L, "정책 위반"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 게시물이면 POST_NOT_FOUND")
    void 게시물_없음() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(ADMIN_ID, 99L, "정책 위반"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("댓글을 강제 삭제하면 내용은 비고 누가 왜 지웠는지 남는다")
    void 댓글_삭제() {
        Post post = Post.builder().author(author).content("내용").shareType(ShareType.ARCHIVE).build();
        Comment comment = Comment.on(post, author, "댓글 내용");
        given(commentRepository.findById(2L)).willReturn(Optional.of(comment));

        service.deleteComment(ADMIN_ID, 2L, "욕설");

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getContent()).isNull();
        assertThat(comment.getDeletedByAdmin()).isEqualTo(admin);
        assertThat(comment.getDeleteReason()).isEqualTo("욕설");
    }

    @Test
    @DisplayName("대댓글도 같은 로직으로 삭제된다 — 저장이 댓글과 같은 테이블이라서다")
    void 대댓글_삭제() {
        Post post = Post.builder().author(author).content("내용").shareType(ShareType.ARCHIVE).build();
        Comment parent = Comment.on(post, author, "부모 댓글");
        Comment reply = Comment.replyTo(parent, author, "대댓글 내용");
        given(commentRepository.findById(3L)).willReturn(Optional.of(reply));

        service.deleteComment(ADMIN_ID, 3L, "욕설");

        assertThat(reply.isDeleted()).isTrue();
        assertThat(reply.getDeletedByAdmin()).isEqualTo(admin);
    }

    @Test
    @DisplayName("없는 댓글이면 COMMENT_NOT_FOUND")
    void 댓글_없음() {
        given(commentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(ADMIN_ID, 99L, "욕설"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }
}
