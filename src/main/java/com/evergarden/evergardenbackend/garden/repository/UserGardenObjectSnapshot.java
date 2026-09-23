package com.evergarden.evergardenbackend.garden.repository;

import java.time.LocalDateTime;

/**
 * {@link UserGardenObjectRepository#findSnapshot}의 결과(인터페이스 프로젝션).
 *
 * <p>엔티티로 다시 읽지 않는 이유 — 같은 트랜잭션 안에서 같은 ID를 이미 한 번 읽은 적
 * 있으면, Hibernate가 SELECT는 실제로 날려도 결과를 1차 캐시에 있는 그 기존 인스턴스로
 * 바꿔치기한다(아이덴티티 맵). 성장 경합에서 진 쪽이 "최신 상태를 다시 읽으려" 해도
 * 자기가 애초에 읽었던(아직 안 자란) 값을 그대로 돌려받게 되는 문제가 실전에서
 * 확인됐다 — 엔티티가 아닌 이 프로젝션으로 우회한다.
 */
public interface UserGardenObjectSnapshot {

    Short getStage();

    LocalDateTime getLastGrownAt();
}
