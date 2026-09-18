package com.evergarden.evergardenbackend.place.repository;

import com.evergarden.evergardenbackend.place.entity.Region;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, String> {

    /** 시/도 코드 아래의 시군구들(PLAN-06 — 시/도로 검색하면 하위 시군구까지 포함한다). */
    List<Region> findByParent_Code(String parentCode);
}
