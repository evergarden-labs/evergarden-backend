package com.evergarden.evergardenbackend.timecapsule.repository;

import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus;
import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, Long> {

    /** 위치 해제 판정(TC-04) 대상 — 아직 안 열렸고 위치 조건인 것만. 개인 규모라 페이지 없이 전부 본다. */
    List<TimeCapsule> findByOwner_IdAndUnlockTypeAndStatus(Long ownerId, UnlockType unlockType, TimeCapsuleStatus status);

    /** 봉인·해제 함께, 최신순(TC-02). 커서는 id 내림차순(ADR-011·ADR-058). */
    @Query("""
            SELECT c FROM TimeCapsule c
            WHERE c.owner.id = :userId
              AND (:cursorId IS NULL OR c.id < :cursorId)
            ORDER BY c.id DESC
            """)
    List<TimeCapsule> findAllByOwner(@Param("userId") Long userId, @Param("cursorId") Long cursorId, Pageable pageable);

    /**
     * 열어본 것만 최근에 연 순서로(TC-06). {@code openedAt}만으로 커서를 만들면 두 캡슐이
     * 같은 순간(같은 요청 안 등)에 열려도 정렬이 흔들릴 수 있어 {@code id}를 동률 기준으로
     * 같이 둔다 — 인기 피드 커서와 같은 이유의 키셋 페이지네이션이다.
     */
    @Query("""
            SELECT c FROM TimeCapsule c
            WHERE c.owner.id = :userId
              AND c.status = com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus.OPENED
              AND (:cursorOpenedAt IS NULL
                    OR c.openedAt < :cursorOpenedAt
                    OR (c.openedAt = :cursorOpenedAt AND c.id < :cursorId))
            ORDER BY c.openedAt DESC, c.id DESC
            """)
    List<TimeCapsule> findOpenedByOwner(@Param("userId") Long userId,
                                        @Param("cursorOpenedAt") LocalDateTime cursorOpenedAt,
                                        @Param("cursorId") Long cursorId, Pageable pageable);
}
