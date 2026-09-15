package com.evergarden.evergardenbackend.archive.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveCoverRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemResponse;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemUpdateRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemsAddRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveLayoutRequest;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.entity.ArchiveLayout;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 아카이브에 배치된 사진·영상 관리(ARCH-06·07·08). */
@Service
@RequiredArgsConstructor
@Transactional
public class ArchiveItemService {

    private final ArchiveRepository archiveRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveCollaboratorRepository collaboratorRepository;
    private final MediaRepository mediaRepository;
    private final ArchiveAccessGuard accessGuard;
    private final ArchiveMapper archiveMapper;

    public List<ArchiveItemResponse> addItems(Long userId, Long archiveId, ArchiveItemsAddRequest request) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkEditable(archive, userId);

        List<Long> mediaIds = request.items().stream().map(ArchiveItemsAddRequest.Item::mediaId).toList();
        Map<Long, Media> mediaById = mediaRepository.findAllById(mediaIds).stream()
                .collect(Collectors.toMap(Media::getId, m -> m));
        for (Long mediaId : mediaIds) {
            Media media = mediaById.get(mediaId);
            // PENDING(업로드 미완료)인 것도 못 찾은 것과 같게 본다 — 아직 못 쓰는 상태이기는 마찬가지다
            if (media == null || !media.isReady()) {
                throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
            }
            if (!media.isUploadedBy(userId)) {
                throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
            }
        }
        request.items().forEach(item -> validateLayout(item.layout()));

        short nextOrder = (short) (archiveItemRepository.countByArchive(archive) + 1);
        List<ArchiveItem> created = new ArrayList<>();
        List<ArchiveItemsAddRequest.Item> items = request.items();
        for (int i = 0; i < items.size(); i++) {
            ArchiveItemsAddRequest.Item item = items.get(i);
            ArchiveLayout layout = item.layout() != null ? item.layout() : defaultLayout(i);
            created.add(ArchiveItem.builder()
                    .archive(archive)
                    .media(mediaById.get(item.mediaId()))
                    .sortOrder((short) (nextOrder + i))
                    .layout(layout)
                    .caption(item.caption())
                    .build());
        }
        archiveItemRepository.saveAll(created);
        refreshPeriod(archive);

        return created.stream().map(item -> archiveMapper.toItemResponse(archive, item)).toList();
    }

    public ArchiveItemResponse updateItem(Long userId, Long archiveId, Long itemId,
                                          ArchiveItemUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Archive archive = findArchive(archiveId);
        accessGuard.checkEditable(archive, userId);
        ArchiveItem item = findItem(archive, itemId);
        validateLayout(request.layout());

        item.update(request.sortOrder(), request.layout(), request.caption());
        return archiveMapper.toItemResponse(archive, item);
    }

    public void removeItem(Long userId, Long archiveId, Long itemId) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkEditable(archive, userId);
        ArchiveItem item = findItem(archive, itemId);

        if (archive.getCoverItem() != null && archive.getCoverItem().getId().equals(itemId)) {
            archive.changeCover(null);
        }
        archiveItemRepository.delete(item);
        renumberRemaining(archive);
        refreshPeriod(archive);
    }

    /** 전체 항목을 통째로 다시 배치한다(ARCH-07). 부분 전송은 거부한다. */
    public ArchiveDetail replaceLayout(Long userId, Long archiveId, ArchiveLayoutRequest request) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkEditable(archive, userId);

        List<ArchiveItem> current = archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive);
        if (current.size() != request.items().size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Map<Long, ArchiveItem> byId = current.stream()
                .collect(Collectors.toMap(ArchiveItem::getId, i -> i));

        for (ArchiveLayoutRequest.Item req : request.items()) {
            if (!byId.containsKey(req.itemId())) {
                throw new BusinessException(ErrorCode.ARCHIVE_ITEM_NOT_FOUND);
            }
            validateLayout(req.layout());
        }
        for (ArchiveLayoutRequest.Item req : request.items()) {
            byId.get(req.itemId()).reorder(req.sortOrder(), req.layout());
        }
        return toDetail(archive, userId);
    }

    public ArchiveDetail setCover(Long userId, Long archiveId, ArchiveCoverRequest request) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkEditable(archive, userId);
        ArchiveItem item = findItem(archive, request.itemId());

        if (item.getMedia().getType() != MediaType.IMAGE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        archive.changeCover(item);
        return toDetail(archive, userId);
    }

    /** 사진 없이 담을 때 서버가 채우는 기본 배치. 세로로 겹치지 않게 늘어놓기만 한다 —
     *  어차피 앱이 다시 배치하는 것을 전제로 한 값이라 정교할 필요가 없다. */
    private ArchiveLayout defaultLayout(int index) {
        double height = 0.3;
        double y = Math.min(index * (height + 0.05), 1 - height);
        return new ArchiveLayout(0.1, y, 0.8, height, 0);
    }

    private void validateLayout(ArchiveLayout layout) {
        if (layout == null) {
            return;
        }
        if (outOfRange(layout.x()) || outOfRange(layout.y())
                || outOfRange(layout.width()) || outOfRange(layout.height())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private boolean outOfRange(double value) {
        return value < 0 || value > 1;
    }

    private void renumberRemaining(Archive archive) {
        List<ArchiveItem> remaining = archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive);
        short order = 1;
        for (ArchiveItem item : remaining) {
            item.reorder(order++, item.getLayout());
        }
    }

    /** 담긴 사진의 촬영일 범위로 여행 기간을 다시 계산한다(ADR-030). */
    private void refreshPeriod(Archive archive) {
        List<ArchiveItem> items = archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive);
        List<LocalDate> takenDates = items.stream()
                .map(item -> item.getMedia().getTakenAt())
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .toList();
        LocalDate start = takenDates.stream().min(Comparator.naturalOrder()).orElse(null);
        LocalDate end = takenDates.stream().max(Comparator.naturalOrder()).orElse(null);
        archive.refreshPeriod(start, end);
    }

    private ArchiveItem findItem(Archive archive, Long itemId) {
        ArchiveItem item = archiveItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_ITEM_NOT_FOUND));
        if (!item.getArchive().getId().equals(archive.getId())) {
            throw new BusinessException(ErrorCode.ARCHIVE_ITEM_NOT_FOUND);
        }
        return item;
    }

    private Archive findArchive(Long archiveId) {
        return archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));
    }

    private ArchiveDetail toDetail(Archive archive, Long userId) {
        List<ArchiveItem> items = archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive);
        List<ArchiveCollaborator> collaborators = collaboratorRepository.findByArchive(archive);
        return archiveMapper.toDetail(archive, items, collaborators, userId);
    }
}
