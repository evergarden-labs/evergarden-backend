package com.evergarden.evergardenbackend.media.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 올릴 파일 한 건의 정보. {@code POST /media/upload-urls}가 이걸 배열로 받는다.
 *
 * @param width      영상이면 필수(ADR-052). 사진은 서버가 EXIF에서 읽으므로 생략 가능
 * @param height     영상이면 필수
 * @param durationMs 영상이면 필수. 앱이 보내는 값이라 서버가 검증하지 못한다
 */
public record MediaUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank String contentType,
        @NotNull @Min(1) Long sizeBytes,
        Integer width,
        Integer height,
        Integer durationMs) {
}
