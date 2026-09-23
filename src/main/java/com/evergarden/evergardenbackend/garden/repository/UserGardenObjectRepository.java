package com.evergarden.evergardenbackend.garden.repository;

import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGardenObjectRepository extends JpaRepository<UserGardenObject, Long> {

    /** 해금한 것만(GARDEN-01) — 아직 안 해금한 건 이 테이블에 행 자체가 없다. */
    List<UserGardenObject> findByUser_Id(Long userId);
}
