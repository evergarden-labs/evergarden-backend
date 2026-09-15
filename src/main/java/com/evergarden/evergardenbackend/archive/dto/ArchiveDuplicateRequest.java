package com.evergarden.evergardenbackend.archive.dto;

import jakarta.validation.constraints.Size;

/** {@code POST /archives/{archiveId}/duplicate}(ARCH-15)의 요청 본문. 본문 자체를 생략할 수 있다. */
public record ArchiveDuplicateRequest(@Size(max = 60) String title) {
}
