package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    long countByParent(Comment parent);

    /** 댓글 상세에 앞의 몇 개만 미리 담는 용도(작성 순서). 나머지는 {@code listReplies}로. */
    List<Comment> findTop3ByParentOrderByIdAsc(Comment parent);
}
