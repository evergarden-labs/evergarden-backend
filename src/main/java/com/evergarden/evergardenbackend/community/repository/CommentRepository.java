package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.Post;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    long countByParent(Comment parent);

    /** 댓글 상세에 앞의 몇 개만 미리 담는 용도(작성 순서). 나머지는 {@code listReplies}로. */
    List<Comment> findTop3ByParentOrderByIdAsc(Comment parent);

    /**
     * 게시물의 최상위 댓글을 작성 순서로(COMM-03). 삭제된 댓글도 "삭제된 댓글입니다"로
     * 표시해야 해서(ADR-007) 상태로 거르지 않는다. 커서는 id 오름차순(ADR-011·ADR-058) —
     * 아카이브 목록의 최신순 내림차순과 반대 방향이라 부등호가 다르다.
     */
    @Query("""
            SELECT c FROM Comment c JOIN FETCH c.author
            WHERE c.post = :post AND c.parent IS NULL
              AND (:cursorId IS NULL OR c.id > :cursorId)
            ORDER BY c.id ASC
            """)
    List<Comment> findTopLevelAfter(@Param("post") Post post, @Param("cursorId") Long cursorId, Pageable pageable);

    /** 댓글의 대댓글을 작성 순서로(COMM-03). 마찬가지로 삭제 여부로 거르지 않는다. */
    @Query("""
            SELECT c FROM Comment c JOIN FETCH c.author
            WHERE c.parent = :parent
              AND (:cursorId IS NULL OR c.id > :cursorId)
            ORDER BY c.id ASC
            """)
    List<Comment> findRepliesAfter(@Param("parent") Comment parent, @Param("cursorId") Long cursorId, Pageable pageable);
}
