package com.evergarden.evergardenbackend.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /posts/{postId}}(COMM-05)의 요청 본문.
 *
 * <p>본문만 고친다. 공유 대상과 지역 스냅샷은 바꿀 수 없다(ADR-045) — 바꾸고 싶으면
 * 지우고 다시 올려야 한다.
 */
public record PostUpdateRequest(@NotBlank @Size(min = 1, max = 2000) String content) {
}
