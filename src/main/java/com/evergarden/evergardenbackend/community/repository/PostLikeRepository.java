package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.PostLike;
import com.evergarden.evergardenbackend.community.entity.PostLikeId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    /**
     * 내가 좋아요한 게시물을 최근 순으로(COMM-08). 좋아요한 뒤 삭제된 게시물은 뺀다 —
     * {@code post_likes} 행은 그대로 있지만 {@code posts.status}로 걸러낸다.
     */
    @Query("""
            SELECT pl FROM PostLike pl JOIN FETCH pl.post p
            WHERE pl.user.id = :userId
              AND p.status = com.evergarden.evergardenbackend.community.entity.PostStatus.ACTIVE
            ORDER BY pl.createdAt DESC
            """)
    Page<PostLike> findActiveLikedByUser(@Param("userId") Long userId, Pageable pageable);
}
