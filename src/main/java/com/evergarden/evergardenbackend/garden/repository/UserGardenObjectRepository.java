package com.evergarden.evergardenbackend.garden.repository;

import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGardenObjectRepository extends JpaRepository<UserGardenObject, Long> {

    /** 해금한 것만(GARDEN-01) — 아직 안 해금한 건 이 테이블에 행 자체가 없다. */
    List<UserGardenObject> findByUser_Id(Long userId);

    /** 이미 해금했는지(GARDEN-02) — 있으면 성장 판정, 없으면 신규 해금. */
    Optional<UserGardenObject> findByUser_IdAndGardenObject_Id(Long userId, Long gardenObjectId);
}
