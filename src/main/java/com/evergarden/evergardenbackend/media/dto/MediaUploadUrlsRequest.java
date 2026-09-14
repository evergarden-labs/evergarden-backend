package com.evergarden.evergardenbackend.media.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** {@code POST /media/upload-urls}의 요청 본문. */
public record MediaUploadUrlsRequest(@NotEmpty @Valid List<MediaUploadRequest> files) {
}
