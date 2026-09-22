package com.evergarden.evergardenbackend.timecapsule.dto;

import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus;
import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import java.time.LocalDateTime;

/** 명세의 {@code TimeCapsuleSummary} 스키마. 목록용이라 내용을 담지 않는다. */
public record TimeCapsuleSummary(
        Long capsuleId,
        String title,
        TimeCapsuleStatus status,
        UnlockType unlockType,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime openedAt) {
}
