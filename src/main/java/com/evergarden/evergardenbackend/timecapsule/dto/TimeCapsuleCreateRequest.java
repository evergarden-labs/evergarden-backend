package com.evergarden.evergardenbackend.timecapsule.dto;

import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code POST /time-capsules}(TC-01)의 요청 본문.
 *
 * <p>해제 조건은 {@code unlockType}이 고른 쪽의 필드만 채운다 — {@code DATE}면
 * {@code unlockDate}, {@code LOCATION}이면 {@code unlockLat}/{@code unlockLng}/
 * {@code unlockRadiusMeters}가 필수다. 어느 쪽이 빠졌는지는 필드 하나짜리
 * 애노테이션으로 못 걸어서 서비스에서 확인한다.
 */
public record TimeCapsuleCreateRequest(
        @NotBlank @Size(min = 1, max = 60) String title,
        @NotBlank @Size(min = 1, max = 5000) String content,
        @NotNull UnlockType unlockType,
        LocalDate unlockDate,
        Double unlockLat,
        Double unlockLng,
        @Min(50) Integer unlockRadiusMeters,
        @Size(max = 60) String placeName,
        List<Long> mediaIds) {
}
