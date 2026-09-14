package com.evergarden.evergardenbackend.media.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 사진의 EXIF에서 촬영 시각·좌표·해상도를 읽는다(ADR-030).
 *
 * <p>카카오톡으로 받은 사진이나 스크린샷은 EXIF가 지워져 있다. 이때뿐 아니라
 * 파싱 자체가 실패해도 요청을 실패시키지 않는다 — 값을 모르는 것으로 보고
 * {@link ExifData}의 해당 필드를 {@code null}로 채운다. 아카이브의 여행 기간(ADR-030)이
 * 이 값에 기대므로, 못 읽었다고 업로드 자체를 막으면 사용자가 사진을 아예 못 올린다.
 */
@Slf4j
@Component
public class ExifReader {

    private static final DateTimeFormatter EXIF_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    public record ExifData(LocalDateTime takenAt, BigDecimal lat, BigDecimal lng,
                            Integer width, Integer height) {

        static final ExifData EMPTY = new ExifData(null, null, null, null, null);
    }

    public ExifData read(byte[] imageBytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
            return new ExifData(readTakenAt(metadata), readLat(metadata), readLng(metadata),
                    readWidth(metadata), readHeight(metadata));
        } catch (Exception e) {
            log.debug("EXIF 읽기 실패, 값 없이 진행합니다", e);
            return ExifData.EMPTY;
        }
    }

    private LocalDateTime readTakenAt(Metadata metadata) {
        ExifSubIFDDirectory dir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (dir == null) {
            return null;
        }
        String raw = dir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, EXIF_DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private GeoLocation geoLocation(Metadata metadata) {
        GpsDirectory dir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        return dir == null ? null : dir.getGeoLocation();
    }

    private BigDecimal readLat(Metadata metadata) {
        GeoLocation location = geoLocation(metadata);
        return location == null ? null : BigDecimal.valueOf(location.getLatitude());
    }

    private BigDecimal readLng(Metadata metadata) {
        GeoLocation location = geoLocation(metadata);
        return location == null ? null : BigDecimal.valueOf(location.getLongitude());
    }

    /** JPEG는 SOF 마커, PNG는 IHDR 청크에서 읽는다. 그 외 형식은 해상도를 못 읽을 수 있다. */
    private Integer readWidth(Metadata metadata) {
        JpegDirectory jpeg = metadata.getFirstDirectoryOfType(JpegDirectory.class);
        if (jpeg != null) {
            return jpeg.getInteger(JpegDirectory.TAG_IMAGE_WIDTH);
        }
        PngDirectory png = metadata.getFirstDirectoryOfType(PngDirectory.class);
        return png == null ? null : png.getInteger(PngDirectory.TAG_IMAGE_WIDTH);
    }

    private Integer readHeight(Metadata metadata) {
        JpegDirectory jpeg = metadata.getFirstDirectoryOfType(JpegDirectory.class);
        if (jpeg != null) {
            return jpeg.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT);
        }
        PngDirectory png = metadata.getFirstDirectoryOfType(PngDirectory.class);
        return png == null ? null : png.getInteger(PngDirectory.TAG_IMAGE_HEIGHT);
    }
}
