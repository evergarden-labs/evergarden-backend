package com.evergarden.evergardenbackend.media.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * 사진 리사이즈본을 만든다(ADR-055). 영상은 만들지 않는다(ADR-052).
 *
 * <p><b>한계</b> — JDK 표준 ImageIO 기반이라 JPEG·PNG만 확실히 지원한다. HEIC·WEBP는
 * 플랫폼에 디코더가 없으면 실패할 수 있다. 실패해도 업로드 자체를 막지 않도록
 * 호출하는 쪽({@link MediaService})에서 예외를 잡고 썸네일 없이 진행한다.
 */
@Component
public class ThumbnailGenerator {

    private static final int MAX_DIMENSION = 480;

    public byte[] generate(byte[] original) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(original))
                .size(MAX_DIMENSION, MAX_DIMENSION)
                .outputFormat("jpg")
                .toOutputStream(out);
        return out.toByteArray();
    }
}
