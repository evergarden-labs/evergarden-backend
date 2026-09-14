package com.evergarden.evergardenbackend.timecapsule.repository;

import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMedia;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMediaId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeCapsuleMediaRepository extends JpaRepository<TimeCapsuleMedia, TimeCapsuleMediaId> {
}
