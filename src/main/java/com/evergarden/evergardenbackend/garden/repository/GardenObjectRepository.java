package com.evergarden.evergardenbackend.garden.repository;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GardenObjectRepository extends JpaRepository<GardenObject, Long> {

    /** 그 지역 전용 오브젝트만(MAP-03). 공통(지역 없음) 오브젝트는 제외 — GARDEN-02 보상이 지역 전용 것만 준다. */
    List<GardenObject> findByRegion_Code(String regionCode);
}
