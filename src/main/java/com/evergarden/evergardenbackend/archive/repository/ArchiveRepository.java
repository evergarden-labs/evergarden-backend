package com.evergarden.evergardenbackend.archive.repository;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {

    boolean existsByTrip_Id(Long tripId);

    /** 일정에 연결된 아카이브(있으면). 플래너의 {@code linkedArchiveId}용(ADR-001). */
    Optional<Archive> findByTrip_Id(Long tripId);

    /** 내가 만들었거나 참여(JOINED) 중인 아카이브. 커서는 id 기준(ADR-058). */
    @Query("""
            SELECT a FROM Archive a
            WHERE (a.owner.id = :userId OR a.id IN (
                    SELECT ac.archive.id FROM ArchiveCollaborator ac
                    WHERE ac.user.id = :userId
                      AND ac.status = com.evergarden.evergardenbackend.archive.entity.CollaboratorStatus.JOINED))
              AND (:cursorId IS NULL OR a.id < :cursorId)
            ORDER BY a.id DESC
            """)
    List<Archive> findAccessible(@Param("userId") Long userId, @Param("cursorId") Long cursorId, Pageable pageable);

    /**
     * 이름 부분 일치 또는 기간 겹침으로 찾는다(ARCH-04). 기간이 없는 아카이브는
     * 기간 검색에 잡히지 않는다(ADR-030).
     */
    @Query("""
            SELECT a FROM Archive a
            WHERE (a.owner.id = :userId OR a.id IN (
                    SELECT ac.archive.id FROM ArchiveCollaborator ac
                    WHERE ac.user.id = :userId
                      AND ac.status = com.evergarden.evergardenbackend.archive.entity.CollaboratorStatus.JOINED))
              AND (:cursorId IS NULL OR a.id < :cursorId)
              AND (:keyword IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND ((:from IS NULL AND :to IS NULL) OR (
                    a.startDate IS NOT NULL AND a.endDate IS NOT NULL
                    AND (:from IS NULL OR a.endDate >= :from)
                    AND (:to IS NULL OR a.startDate <= :to)))
            ORDER BY a.id DESC
            """)
    List<Archive> search(@Param("userId") Long userId, @Param("cursorId") Long cursorId,
                          @Param("keyword") String keyword, @Param("from") LocalDate from,
                          @Param("to") LocalDate to, Pageable pageable);
}
