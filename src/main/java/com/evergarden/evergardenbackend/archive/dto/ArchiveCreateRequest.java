package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /archives}(ARCH-01)의 요청 본문.
 *
 * @param tripId 연결할 여행 일정. 생략 가능하다(ADR-001)
 */
public record ArchiveCreateRequest(
        @NotBlank @Size(min = 1, max = 60) String title,
        @NotNull ArchiveTheme theme,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColor,
        Long tripId) {
}
