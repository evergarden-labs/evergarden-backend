package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 내가 쓴 게시물을 최신순으로(COMM-09). 삭제한 게시물은 뺀다. */
    Page<Post> findByAuthor_IdAndStatusOrderByCreatedAtDesc(Long authorId, PostStatus status, Pageable pageable);
}
