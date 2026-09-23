package com.evergarden.evergardenbackend.garden.repository;

import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGardenObjectRepository extends JpaRepository<UserGardenObject, Long> {

    /** 해금한 것만(GARDEN-01) — 아직 안 해금한 건 이 테이블에 행 자체가 없다. */
    List<UserGardenObject> findByUser_Id(Long userId);

    /** 이미 해금했는지(GARDEN-02) — 있으면 성장 판정, 없으면 신규 해금. */
    Optional<UserGardenObject> findByUser_IdAndGardenObject_Id(Long userId, Long gardenObjectId);

    /**
     * 처음 해금을 시도한다(GARDEN-02). "먼저 있는지 확인하고 없으면 저장"은 두 요청이
     * 거의 동시에 들어오면(더블탭·재시도 등) 둘 다 "없음"을 보고 둘 다 만들려 들 수 있어
     * (ADR-006) 대신 이 한 문장으로 시도한다 — {@code (user_id, garden_object_id)} 유니크
     * 제약과 부딪히면 예외 없이 그냥 아무 일도 안 하고, 몇 행이 실제로 들어갔는지만 돌려준다.
     *
     * @return 실제로 새로 만들었으면 {@code 1}, 이미 있어서 아무 일도 안 했으면 {@code 0}
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_garden_objects (user_id, garden_object_id, stage, unlocked_at)
            VALUES (:userId, :gardenObjectId, 1, :unlockedAt)
            ON CONFLICT (user_id, garden_object_id) DO NOTHING
            """, nativeQuery = true)
    int tryInsertUnlock(@Param("userId") Long userId, @Param("gardenObjectId") Long gardenObjectId,
                         @Param("unlockedAt") LocalDateTime unlockedAt);
}
