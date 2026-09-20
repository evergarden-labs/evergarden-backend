package com.evergarden.evergardenbackend.place.repository;

import com.evergarden.evergardenbackend.place.entity.Place;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByContentId(String contentId);

    /**
     * {@code keyword}·{@code regionCode}·{@code contentTypeId} 모두 선택 조건이다(PLAN-06).
     * {@code filterByRegion}이 {@code false}면 {@code regionCodes}는 무시된다 — JPQL의
     * {@code IN} 절에 {@code null} 컬렉션을 바인딩할 수 없어 빈 리스트 + 플래그로 우회한다.
     */
    @Query("""
            SELECT p FROM Place p
            WHERE (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:filterByRegion = false OR p.region.code IN :regionCodes)
              AND (:contentTypeId IS NULL OR p.contentTypeId = :contentTypeId)
            """)
    Page<Place> search(
            @Param("keyword") String keyword,
            @Param("filterByRegion") boolean filterByRegion,
            @Param("regionCodes") List<String> regionCodes,
            @Param("contentTypeId") String contentTypeId,
            Pageable pageable);

    /** 반경 검색용 대략의 사각 범위 안에 있는 장소들. 정확한 거리 계산은 서비스에서 한다. */
    List<Place> findByLatBetweenAndLngBetween(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);
}
