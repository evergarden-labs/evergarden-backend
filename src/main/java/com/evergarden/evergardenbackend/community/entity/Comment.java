package com.evergarden.evergardenbackend.community.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글과 대댓글. 같은 테이블에 두고 {@link #parent}로 구분한다.
 *
 * <p>깊이는 1단계까지다. 대댓글에 또 대댓글을 달 수 없다(COMM-14).
 */
@Entity
@Getter
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    /** 대댓글이면 부모 댓글, 댓글이면 {@code null} */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parent;

    /** 삭제되면 비운다. 대댓글이 달려 있으면 자리는 남는다(ADR-007) */
    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommentStatus status;

    /**
     * 관리자가 강제 삭제했으면 그 관리자(ADMIN-07·08 — 대댓글도 같은 테이블이라 같이 쓴다).
     * 작성자 본인 삭제(COMM-13·16)면 {@code null}
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_admin_id")
    private Admin deletedByAdmin;

    @Column(name = "delete_reason", length = 200)
    private String deleteReason;

    private Comment(Post post, User author, Comment parent, String content) {
        this.post = post;
        this.author = author;
        this.parent = parent;
        this.content = content;
        this.status = CommentStatus.ACTIVE;
    }

    /** 게시물에 댓글을 단다(COMM-11). */
    public static Comment on(Post post, User author, String content) {
        return new Comment(post, author, null, content);
    }

    /** 댓글에 답글을 단다(COMM-14). 부모가 이미 대댓글이면 서비스가 먼저 막는다. */
    public static Comment replyTo(Comment parent, User author, String content) {
        return new Comment(parent.getPost(), author, parent, content);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    /**
     * 삭제한다(COMM-13 · COMM-16).
     *
     * <p>내용만 비우고 행은 남긴다. 물리 삭제하면 남이 쓴 대댓글까지 사라진다.
     * 앱은 자리만 남은 댓글을 "삭제된 댓글입니다"로 표시한다.
     */
    public void delete() {
        this.status = CommentStatus.DELETED;
        this.content = null;
    }

    /**
     * 관리자가 정책 위반으로 강제 삭제한다(ADMIN-07·08). {@link #delete()}와 같이
     * 내용을 비우고 자리는 남기되(ADR-007), 누가 왜 지웠는지를 같이 남긴다.
     */
    public void adminDelete(Admin admin, String reason) {
        this.status = CommentStatus.DELETED;
        this.content = null;
        this.deletedByAdmin = admin;
        this.deleteReason = reason;
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }
}
