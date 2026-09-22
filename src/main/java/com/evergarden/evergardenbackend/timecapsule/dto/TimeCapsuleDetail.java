package com.evergarden.evergardenbackend.timecapsule.dto;

import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus;
import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 명세의 {@code TimeCapsuleDetail} 스키마 — {@code TimeCapsuleSummary}에
 * {@code unlockCondition}·{@code content}·{@code media}를 더한 모양이다.
 *
 * <p>{@code content}·{@code media}는 {@code status}가 {@code OPENED}일 때만 채운다 —
 * 봉인된 캡슐의 내용이 여기서 새면 타임캡슐이 성립하지 않는다. 이 규칙은
 * {@code TimeCapsuleMapper}가 지킨다.
 */
public record TimeCapsuleDetail(
        Long capsuleId,
        String title,
        TimeCapsuleStatus status,
        UnlockType unlockType,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime openedAt,
        UnlockCondition unlockCondition,
        String content,
        List<MediaResponse> media) {
}
