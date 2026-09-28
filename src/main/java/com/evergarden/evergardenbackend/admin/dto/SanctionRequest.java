package com.evergarden.evergardenbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 명세의 {@code SanctionRequest} 스키마. 경고·차단·차단 해제·강제 삭제가 공통으로 쓴다. */
public record SanctionRequest(@NotBlank @Size(max = 200) String reason) {
}
