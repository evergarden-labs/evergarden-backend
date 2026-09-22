package com.evergarden.evergardenbackend.timecapsule.dto;

import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import java.time.LocalDate;

/** 명세의 {@code UnlockCondition} 스키마. */
public record UnlockCondition(
        UnlockType type,
        boolean satisfied,
        LocalDate unlockDate,
        Double lat,
        Double lng,
        Integer radiusMeters,
        String placeName) {
}
