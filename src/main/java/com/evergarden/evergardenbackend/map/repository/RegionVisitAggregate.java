package com.evergarden.evergardenbackend.map.repository;

import java.time.LocalDateTime;

/** 사용자별 지역 방문 집계(인터페이스 프로젝션). {@link RegionVisitRepository#aggregateByUser} 결과. */
public interface RegionVisitAggregate {

    String getRegionCode();

    long getVisitCount();

    LocalDateTime getLastVisitedAt();
}
