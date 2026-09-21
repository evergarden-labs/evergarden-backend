package com.evergarden.evergardenbackend.community.entity;

import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 좋아요(COMM-07).
 *
 * <p>중복은 복합키가 DB에서 막는다. 코드로 "이미 있는지 확인 후 저장"하면
 * 동시 요청에서 새어 나간다(ADR-006) — {@code PostService.like()}가 존재
 * 확인 없이 바로 저장을 시도하고 제약 위반을 잡는 이유다.
 *
 * <p>그런데 이 제약이 실제로 걸리려면 저장이 진짜 INSERT여야 한다.
 * {@code @EmbeddedId}는 생성자에서 이미 값이 채워지므로, {@link Persistable}을
 * 구현하지 않으면 Spring Data가 "이미 있는 행"으로 보고 {@code merge()}를 써서
 * 중복 저장을 조용히 UPDATE로 처리해 버린다(실전에서 확인 — 중복 좋아요가
 * 막히지 않고 그냥 성공했었다). {@link #isNew()}가 항상 {@code true}인 이유는
 * {@code PostLike}가 생성만 있고 수정은 없는 엔티티라서다.
 *
 * <p>{@code BaseTimeEntity}를 상속하지 않고 {@code createdAt}만 직접 두지만(연결
 * 테이블이라 수정 시각은 필요 없음), {@code @CreatedDate}가 실제로 채워지려면
 * {@link AuditingEntityListener}가 있어야 한다 — 이게 빠지면 자동으로 null이
 * 돼서 {@code created_at NOT NULL} 제약에 매번 걸린다(실전에서 확인).
 */
@Entity
@Getter
@Table(name = "post_likes")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike implements Persistable<PostLikeId> {

    @EmbeddedId
    private PostLikeId id;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 좋아요 목록을 최근 순으로 보여주는 데 쓴다(COMM-08) */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostLike(Post post, User user) {
        this.id = new PostLikeId(post.getId(), user.getId());
        this.post = post;
        this.user = user;
    }

    @Override
    public PostLikeId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
