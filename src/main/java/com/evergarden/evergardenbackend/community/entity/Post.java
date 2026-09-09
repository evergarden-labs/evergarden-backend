package com.evergarden.evergardenbackend.community.entity;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.trip.entity.Trip;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커뮤니티 게시물. 코스·아카이브·둘 다 중에서 골라 공유한다(COMM-04).
 *
 * <p>두 참조가 모두 {@code null}일 수 있다. 사용자가 원본을 모두 지우면
 * {@code ON DELETE SET NULL}로 비워지는데, "최소 하나는 있어야 한다"는 제약을 걸면
 * 그 삭제 자체가 실패한다(ADR-002). 대신 {@link #shareType}이 원래 무엇을 공유했는지 알려준다.
 */
@Entity
@Getter
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    /** 원본이 사라져도 남는 본문 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", nullable = false, length = 10)
    private ShareType shareType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip sharedTrip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id")
    private Archive sharedArchive;

    /** 인기 피드 정렬용 비정규화 카운터. 최근 30일 안에서만 줄을 세운다(ADR-044) */
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    /** 대댓글을 포함한 수 */
    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PostStatus status;

    @Builder
    private Post(User author, String content, ShareType shareType, Trip sharedTrip, Archive sharedArchive) {
        this.author = author;
        this.content = content;
        this.shareType = shareType;
        this.sharedTrip = sharedTrip;
        this.sharedArchive = sharedArchive;
        this.likeCount = 0;
        this.commentCount = 0;
        this.status = PostStatus.ACTIVE;
    }

    /**
     * 본문만 고친다(COMM-05).
     *
     * <p>공유 대상과 지역은 바꿀 수 없다. 바꾸면 사실상 다른 게시물이 되어
     * 이미 달린 댓글의 맥락이 깨지고 지역 스냅샷도 흔들린다(ADR-045).
     */
    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.status = PostStatus.DELETED;
    }

    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    /** 공유했던 코스가 삭제됐는지(COMM-17). */
    public boolean isSharedTripMissing() {
        return (shareType == ShareType.COURSE || shareType == ShareType.BOTH) && sharedTrip == null;
    }

    /** 공유했던 아카이브가 삭제됐는지(COMM-17). */
    public boolean isSharedArchiveMissing() {
        return (shareType == ShareType.ARCHIVE || shareType == ShareType.BOTH) && sharedArchive == null;
    }

    public int increaseLikeCount() {
        return ++this.likeCount;
    }

    public int decreaseLikeCount() {
        return this.likeCount > 0 ? --this.likeCount : 0;
    }

    public void increaseCommentCount() {
        this.commentCount++;
    }

    public void decreaseCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }
}
