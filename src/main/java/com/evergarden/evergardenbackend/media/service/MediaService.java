package com.evergarden.evergardenbackend.media.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.config.StorageProperties;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.dto.MediaUploadRequest;
import com.evergarden.evergardenbackend.media.dto.MediaUploadTicket;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MEDIA-01. 업로드 URL 발급과 완료 처리를 맡는다(ADR-023 · ADR-055). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MediaService {

    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final ContentTypePolicy contentTypePolicy;
    private final MediaKeyGenerator keyGenerator;
    private final MediaStorageService storageService;
    private final ExifReader exifReader;
    private final ThumbnailGenerator thumbnailGenerator;
    private final StorageProperties storageProperties;

    /** {@code POST /media/upload-urls} (ARCH 이전 단계 — 실제 업로드는 앱이 S3로 직접 한다). */
    public List<MediaUploadTicket> issueUploadUrls(Long userId, List<MediaUploadRequest> files) {
        checkBatchSize(files.size());

        // 형식·용량·영상 필수값을 먼저 전부 검증한다. 하나라도 걸리면 PENDING 행을
        // 하나도 만들지 않는다 — 절반만 만들어지면 정리할 방법이 없다
        List<MediaType> types = files.stream()
                .map(f -> validateForUpload(f))
                .toList();

        User uploader = userRepository.getReferenceById(userId);
        List<Media> entities = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            MediaUploadRequest file = files.get(i);
            MediaType type = types.get(i);
            String key = keyGenerator.originalKey(userId, contentTypePolicy.extensionOf(file.contentType()));
            entities.add(Media.builder()
                    .uploader(uploader)
                    .type(type)
                    .storageKey(key)
                    .sizeBytes(file.sizeBytes())
                    .width(file.width())
                    .height(file.height())
                    .durationMs(file.durationMs())
                    .build());
        }
        mediaRepository.saveAll(entities);

        List<MediaUploadTicket> tickets = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            Media media = entities.get(i);
            MediaStorageService.UploadTicket ticket =
                    storageService.presignUpload(media.getStorageKey(), files.get(i).contentType());
            tickets.add(new MediaUploadTicket(media.getId(), ticket.url(), ticket.expiresAt()));
        }
        return tickets;
    }

    private MediaType validateForUpload(MediaUploadRequest file) {
        MediaType type = contentTypePolicy.typeOf(file.contentType());
        contentTypePolicy.checkSize(type, file.sizeBytes());
        if (type == MediaType.VIDEO
                && (file.width() == null || file.height() == null || file.durationMs() == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return type;
    }

    /** {@code POST /media/complete}. */
    public List<MediaResponse> completeUpload(Long userId, List<Long> mediaIds) {
        checkBatchSize(mediaIds.size());

        Map<Long, Media> found = mediaRepository.findAllById(mediaIds).stream()
                .collect(Collectors.toMap(Media::getId, m -> m));
        List<Long> notFound = mediaIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!notFound.isEmpty()) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }

        // 요청 순서를 그대로 유지한다 — 명세가 응답 순서를 보장한다
        List<Media> targets = mediaIds.stream().map(found::get).toList();
        for (Media media : targets) {
            if (!media.isUploadedBy(userId)) {
                throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
            }
            if (media.isReady()) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
            }
        }

        List<Long> incomplete = targets.stream()
                .filter(m -> !storageService.exists(m.getStorageKey()))
                .map(Media::getId)
                .toList();
        if (!incomplete.isEmpty()) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INCOMPLETE, Map.of("mediaIds", incomplete));
        }

        for (Media media : targets) {
            if (media.getType() == MediaType.IMAGE) {
                completeImage(media);
            } else {
                // 영상은 EXIF를 읽지 않는다. 해상도·길이는 발급 시점에 이미 저장돼 있다(ADR-052)
                media.complete(null, null, null, null, null, null);
            }
        }

        return targets.stream().map(this::toResponse).toList();
    }

    private void completeImage(Media media) {
        byte[] original = storageService.download(media.getStorageKey());
        ExifReader.ExifData exif = exifReader.read(original);

        String thumbnailKey = null;
        try {
            byte[] thumbnail = thumbnailGenerator.generate(original);
            thumbnailKey = keyGenerator.thumbnailKeyFor(media.getStorageKey());
            storageService.upload(thumbnailKey, thumbnail, "image/jpeg");
        } catch (Exception e) {
            // HEIC·WEBP는 플랫폼에 디코더가 없으면 실패할 수 있다(ThumbnailGenerator 참고).
            // 썸네일 하나 때문에 업로드 전체를 막지 않는다
            log.warn("썸네일 생성 실패, thumbnailUrl 없이 진행합니다: mediaId={}", media.getId(), e);
        }

        media.complete(thumbnailKey, exif.width(), exif.height(), exif.takenAt(), exif.lat(), exif.lng());
    }

    private MediaResponse toResponse(Media media) {
        String url = storageService.presignDownload(media.getStorageKey());
        String thumbnailUrl = media.getThumbnailKey() != null
                ? storageService.presignDownload(media.getThumbnailKey())
                : null;
        return MediaResponse.of(media, url, thumbnailUrl);
    }

    private void checkBatchSize(int size) {
        if (size > storageProperties.limits().maxFilesPerRequest()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
