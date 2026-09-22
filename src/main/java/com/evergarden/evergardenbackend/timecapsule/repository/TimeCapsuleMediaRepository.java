package com.evergarden.evergardenbackend.timecapsule.repository;

import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMedia;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMediaId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeCapsuleMediaRepository extends JpaRepository<TimeCapsuleMedia, TimeCapsuleMediaId> {

    /** 캡슐을 열었을 때(TC-05) 담긴 사진·영상을 순서대로. */
    List<TimeCapsuleMedia> findByCapsuleOrderBySortOrderAsc(TimeCapsule capsule);
}
