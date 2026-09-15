package com.evergarden.evergardenbackend.archive.dto;

import java.util.List;

/**
 * 명세의 {@code CollaborationSession} 스키마(ARCH-12). STOMP 메시지 규격 자체는
 * {@code docs/realtime-editing.md}에 있다 — 이건 접속에 필요한 정보만 담는다.
 */
public record CollaborationSession(String websocketUrl, String topic, List<CollaboratorResponse> activeEditors) {
}
