package com.evergarden.evergardenbackend.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code Comment} 엔티티의 순수 로직(대댓글 깊이·삭제 정책·소유자)을 확인한다. */
class CommentTest {

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Post post() {
        return Post.builder().author(user(1L)).content("게시물 내용").shareType(ShareType.ARCHIVE).build();
    }

    // ── 대댓글 깊이 ──────────────────────────────────────────

    @Test
    void 댓글은_부모가_없고_대댓글이_아니다() {
        Comment comment = Comment.on(post(), user(1L), "댓글");

        assertThat(comment.getParent()).isNull();
        assertThat(comment.isReply()).isFalse();
    }

    @Test
    void 대댓글은_부모를_가지고_대댓글이다() {
        Comment parent = Comment.on(post(), user(1L), "댓글");

        Comment reply = Comment.replyTo(parent, user(2L), "답글");

        assertThat(reply.getParent()).isSameAs(parent);
        assertThat(reply.isReply()).isTrue();
    }

    @Test
    void 대댓글은_부모와_같은_게시물에_속한다() {
        Post post = post();
        Comment parent = Comment.on(post, user(1L), "댓글");

        Comment reply = Comment.replyTo(parent, user(2L), "답글");

        assertThat(reply.getPost()).isSameAs(post);
    }

    // ── 삭제 정책(ADR-007 · COMM-13·16) ──────────────────────

    @Test
    void 삭제하면_상태가_DELETED로_바뀌고_내용은_비워진다() {
        Comment comment = Comment.on(post(), user(1L), "댓글");

        comment.delete();

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getContent()).isNull();
    }

    @Test
    void 삭제전에는_isDeleted가_false다() {
        Comment comment = Comment.on(post(), user(1L), "댓글");

        assertThat(comment.isDeleted()).isFalse();
    }

    // ── 본문 수정·소유자 ─────────────────────────────────────

    @Test
    void 본문을_고친다() {
        Comment comment = Comment.on(post(), user(1L), "댓글");

        comment.updateContent("고친 댓글");

        assertThat(comment.getContent()).isEqualTo("고친 댓글");
    }

    @Test
    void 작성자_아이디가_같으면_true() {
        Comment comment = Comment.on(post(), user(1L), "댓글");

        assertThat(comment.isWrittenBy(1L)).isTrue();
        assertThat(comment.isWrittenBy(2L)).isFalse();
    }
}
