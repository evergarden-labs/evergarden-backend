package com.evergarden.evergardenbackend.map.repository;

import com.evergarden.evergardenbackend.map.entity.RegionVisit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionVisitRepository extends JpaRepository<RegionVisit, Long> {

    /**
     * 사용자가 실제로 인증한 지역별 방문 횟수·최근 방문 시각(MAP-02). 방문한 적 없는
     * 지역은 여기 안 나온다 — 전체 지역과 합치는 건 호출하는 쪽(서비스)이 한다.
     */
    @Query("""
            SELECT rv.region.code AS regionCode, COUNT(rv) AS visitCount, MAX(rv.verifiedAt) AS lastVisitedAt
            FROM RegionVisit rv
            WHERE rv.user.id = :userId
            GROUP BY rv.region.code
            """)
    List<RegionVisitAggregate> aggregateByUser(@Param("userId") Long userId);

    /** 그 지역에 인증 기록이 이미 있는지(MAP-01의 {@code isFirstVisit} 판정). */
    boolean existsByUser_IdAndRegion_Code(Long userId, String regionCode);
}
