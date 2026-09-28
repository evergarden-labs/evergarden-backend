package com.evergarden.evergardenbackend.admin.dto;

import com.evergarden.evergardenbackend.report.entity.Sanction;
import com.evergarden.evergardenbackend.report.entity.SanctionSource;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import java.time.LocalDateTime;

/** 명세의 {@code Sanction} 스키마. {@code issuedByAdminName}은 자동 제재면 {@code null}이다. */
public record SanctionResponse(
        Long sanctionId,
        Long userId,
        SanctionType type,
        SanctionSource source,
        String reason,
        String issuedByAdminName,
        LocalDateTime createdAt) {

    public static SanctionResponse of(Sanction sanction) {
        return new SanctionResponse(
                sanction.getId(),
                sanction.getUser().getId(),
                sanction.getType(),
                sanction.getSource(),
                sanction.getReason(),
                sanction.getIssuedBy() == null ? null : sanction.getIssuedBy().getName(),
                sanction.getCreatedAt());
    }
}
