package com.evergarden.evergardenbackend.global.response;

import java.util.List;

/**
 * 커서 방식 목록 하나(ADR-011). 아카이브 목록, 타임캡슐 목록, 커뮤니티 피드처럼
 * 같은 페이지네이션 방식을 쓰는 목록 서비스가 공통으로 돌려주는 모양이다.
 */
public record CursorPage<T>(List<T> items, CursorMeta meta) {
}
