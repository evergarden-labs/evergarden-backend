package com.evergarden.evergardenbackend.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글·대댓글 작성·수정 공통 요청 본문(COMM-11·12·14·15).
 * {@code createComment}/{@code updateComment}/{@code createReply}/{@code updateReply}가
 * 명세에서도 같은 스키마({@code CommentWriteRequest})를 쓴다.
 */
public record CommentWriteRequest(@NotBlank @Size(min = 1, max = 500) String content) {
}
