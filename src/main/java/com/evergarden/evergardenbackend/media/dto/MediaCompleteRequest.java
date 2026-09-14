package com.evergarden.evergardenbackend.media.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** {@code POST /media/complete}의 요청 본문. */
public record MediaCompleteRequest(@NotEmpty List<Long> mediaIds) {
}
