package com.evergarden.evergardenbackend.community.repository;

import com.evergarden.evergardenbackend.community.entity.PostRegion;
import com.evergarden.evergardenbackend.community.entity.PostRegionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRegionRepository extends JpaRepository<PostRegion, PostRegionId> {
}
