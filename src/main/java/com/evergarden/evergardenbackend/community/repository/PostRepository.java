package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 내가 쓴 게시물을 최신순으로(COMM-09). 삭제한 게시물은 뺀다. */
    Page<Post> findByAuthor_IdAndStatusOrderByCreatedAtDesc(Long authorId, PostStatus status, Pageable pageable);

    /**
     * 최신 피드(COMM-01·02). {@code regionCode}가 있으면 그 지역이 걸린 게시물만(스냅샷
     * 기준, ADR-003) — {@code listPosts}/{@code listRegionPosts}가 이 쿼리 하나를
     * 같이 쓴다. 커서는 id 내림차순(ADR-011·ADR-058).
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.status = com.evergarden.evergardenbackend.community.entity.PostStatus.ACTIVE
              AND (:regionCode IS NULL OR p.id IN (
                    SELECT pr.post.id FROM PostRegion pr WHERE pr.region.code = :regionCode))
              AND (:cursorId IS NULL OR p.id < :cursorId)
            ORDER BY p.id DESC
            """)
    List<Post> findLatest(@Param("regionCode") String regionCode, @Param("cursorId") Long cursorId, Pageable pageable);

    /**
     * 인기 피드(COMM-01·02, ADR-019). 최근 30일 게시물만 좋아요 순으로 줄 세운다(ADR-044).
     *
     * <p>좋아요 수가 같은 게시물이 흔해서(특히 0개) {@code id}만으로 커서를 만들면 페이지
     * 사이에서 항목이 통째로 빠질 수 있다 — 정렬 기준과 똑같이 {@code (likeCount, id)}
     * 두 값을 커서로 같이 넘겨야 한다(키셋 페이지네이션).
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.status = com.evergarden.evergardenbackend.community.entity.PostStatus.ACTIVE
              AND (:regionCode IS NULL OR p.id IN (
                    SELECT pr.post.id FROM PostRegion pr WHERE pr.region.code = :regionCode))
              AND p.createdAt >= :since
              AND (:cursorLikeCount IS NULL
                    OR p.likeCount < :cursorLikeCount
                    OR (p.likeCount = :cursorLikeCount AND p.id < :cursorId))
            ORDER BY p.likeCount DESC, p.id DESC
            """)
    List<Post> findPopular(@Param("regionCode") String regionCode, @Param("since") LocalDateTime since,
                           @Param("cursorLikeCount") Integer cursorLikeCount, @Param("cursorId") Long cursorId,
                           Pageable pageable);
}
