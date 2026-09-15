package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveLayout;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code PUT /archives/{archiveId}/items/layout}(ARCH-07)의 요청 본문.
 * 아카이브의 모든 항목을 보내야 한다 — 부분 전송은 거부된다.
 */
public record ArchiveLayoutRequest(@NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotNull Long itemId,
            @NotNull @Min(1) Short sortOrder,
            @NotNull ArchiveLayout layout) {
    }
}
