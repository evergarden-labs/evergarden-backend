package com.evergarden.evergardenbackend.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EXIF가 없거나 파일 자체가 깨져도 예외를 던지지 않는지 확인한다(ADR-030).
 * 여기서 만드는 JPEG는 EXIF 없이 순수 픽셀 데이터만 있다 — 카톡 사진·스크린샷과 같은 상황이다.
 */
class ExifReaderTest {

    private final ExifReader exifReader = new ExifReader();

    @Test
    @DisplayName("EXIF가 없는 사진은 촬영 시각·좌표가 null이다")
    void EXIF_없는_사진() throws IOException {
        ExifReader.ExifData data = exifReader.read(plainJpeg(10, 10));

        assertThat(data.takenAt()).isNull();
        assertThat(data.lat()).isNull();
        assertThat(data.lng()).isNull();
    }

    @Test
    @DisplayName("이미지가 아닌 바이트를 줘도 예외 없이 빈 값을 돌려준다")
    void 파일이_아닌_바이트() {
        ExifReader.ExifData data = exifReader.read("이건 사진이 아니다".getBytes());

        assertThat(data.takenAt()).isNull();
        assertThat(data.lat()).isNull();
        assertThat(data.lng()).isNull();
        assertThat(data.width()).isNull();
        assertThat(data.height()).isNull();
    }

    @Test
    @DisplayName("빈 배열을 줘도 예외 없이 빈 값을 돌려준다")
    void 빈_배열() {
        ExifReader.ExifData data = exifReader.read(new byte[0]);

        assertThat(data).isEqualTo(new ExifReader.ExifData(null, null, null, null, null));
    }

    private byte[] plainJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
