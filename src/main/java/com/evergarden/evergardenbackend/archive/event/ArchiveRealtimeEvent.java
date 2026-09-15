package com.evergarden.evergardenbackend.archive.event;

/**
 * HTTP 오퍼레이션이 저장을 마치면 발행하는 이벤트(ADR-051 · ADR-028).
 *
 * <p>{@code type}은 {@code docs/realtime-editing.md} 3.1절의 이벤트 이름을 그대로 쓴다
 * (예: {@code item.added}). 트랜잭션 커밋 후에만 실제로 나가야 하므로, 서비스는
 * 이 이벤트를 스프링 이벤트로 발행하고 {@code ArchiveRealtimeEventListener}가
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받아 STOMP로 전달한다.
 */
public record ArchiveRealtimeEvent(Long archiveId, Long actorUserId, String type, Object payload) {
}
