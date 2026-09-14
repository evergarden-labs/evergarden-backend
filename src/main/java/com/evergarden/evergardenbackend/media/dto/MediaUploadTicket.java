package com.evergarden.evergardenbackend.media.dto;

import java.time.Instant;

/**
 * @param mediaId   업로드를 마친 뒤 {@code POST /media/complete}에 그대로 보낸다
 * @param uploadUrl 이 주소에 파일을 PUT으로 올린다. 이 API가 아니라 스토리지로 직접 보낸다
 */
public record MediaUploadTicket(Long mediaId, String uploadUrl, Instant expiresAt) {
}
