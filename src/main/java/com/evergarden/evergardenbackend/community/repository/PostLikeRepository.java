package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.PostLike;
import com.evergarden.evergardenbackend.community.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
}
