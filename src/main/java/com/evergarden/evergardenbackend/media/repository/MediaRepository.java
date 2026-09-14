package com.evergarden.evergardenbackend.media.repository;

import com.evergarden.evergardenbackend.media.entity.Media;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<Media, Long> {
}
