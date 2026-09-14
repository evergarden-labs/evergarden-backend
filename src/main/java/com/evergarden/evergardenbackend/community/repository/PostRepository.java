package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.Post;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}
