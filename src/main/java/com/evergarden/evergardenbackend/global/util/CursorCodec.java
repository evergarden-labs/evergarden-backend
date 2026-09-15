package com.evergarden.evergardenbackend.global.util;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 커서 페이지네이션 토큰(ADR-011 · ADR-058).
 *
 * <p>정렬 기준이 {@code id DESC} 하나뿐인 목록에서 쓴다. 원래 값은 단순한 정수 id지만,
 * {@code CursorMeta}가 "서버가 만든 불투명한 문자열"이라고 정해뒀으므로 그대로 노출하지 않고
 * Base64로 감싼다.
 */
public final class CursorCodec {

    private CursorCodec() {
    }

    public static String encode(Long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** {@code cursor} 파라미터가 없으면(첫 페이지) {@code null}을 돌려준다. */
    public static Long decode(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
