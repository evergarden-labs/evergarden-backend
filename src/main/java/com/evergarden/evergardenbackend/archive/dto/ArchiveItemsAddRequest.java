package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveLayout;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code POST /archives/{archiveId}/items}(ARCH-06)의 요청 본문. */
public record ArchiveItemsAddRequest(@NotEmpty @Size(max = 20) @Valid List<Item> items) {

    /**
     * @param layout  생략하면 서버가 기본 배치를 채운다
     * @param caption 사진에 붙이는 짧은 글(ADR-029)
     */
    public record Item(
            @NotNull Long mediaId,
            ArchiveLayout layout,
            @Size(max = 300) String caption) {
    }
}
