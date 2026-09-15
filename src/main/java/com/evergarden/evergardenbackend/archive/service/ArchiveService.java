package com.evergarden.evergardenbackend.archive.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveCreateRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.archive.dto.ArchiveUpdateRequest;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.CursorMeta;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.util.CursorCodec;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 아카이브 기본 CRUD(ARCH-01·02·03·04·05·09). */
@Service
@RequiredArgsConstructor
@Transactional
public class ArchiveService {

    private final ArchiveRepository archiveRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveCollaboratorRepository collaboratorRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final ArchiveAccessGuard accessGuard;
    private final ArchiveMapper archiveMapper;

    public ArchiveDetail create(Long userId, ArchiveCreateRequest request) {
        User owner = userRepository.getReferenceById(userId);
        Trip trip = linkableTrip(userId, request.tripId());

        Archive archive = Archive.builder()
                .owner(owner)
                .trip(trip)
                .title(request.title())
                .theme(request.theme())
                .primaryColor(request.primaryColor())
                .build();
        archiveRepository.save(archive);
        collaboratorRepository.save(ArchiveCollaborator.owner(archive, owner, LocalDateTime.now()));

        return toDetail(archive, userId);
    }

    public ArchiveDetail get(Long userId, Long archiveId) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkViewable(archive, userId);
        return toDetail(archive, userId);
    }

    public ArchiveDetail update(Long userId, Long archiveId, ArchiveUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Archive archive = findArchive(archiveId);
        accessGuard.checkEditable(archive, userId);

        archive.update(request.title(), request.theme(), request.primaryColor());
        return toDetail(archive, userId);
    }

    public void delete(Long userId, Long archiveId) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkOwner(archive, userId);
        archiveRepository.delete(archive);
    }

    public CursorPage<ArchiveSummary> list(Long userId, String cursor, int size) {
        Long cursorId = CursorCodec.decode(cursor);
        List<Archive> archives = archiveRepository.findAccessible(userId, cursorId, PageRequest.of(0, size + 1));
        return toPage(archives, userId, size);
    }

    public CursorPage<ArchiveSummary> search(Long userId, String keyword, LocalDate from, LocalDate to,
                                             String cursor, int size) {
        if (keyword == null && from == null && to == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        Long cursorId = CursorCodec.decode(cursor);
        List<Archive> archives = archiveRepository.search(
                userId, cursorId, keyword, from, to, PageRequest.of(0, size + 1));
        return toPage(archives, userId, size);
    }

    /** {@code tripId}가 있으면 소유·중복 연결을 검증하고 돌려준다. 없으면 {@code null}(ADR-001). */
    private Trip linkableTrip(Long userId, Long tripId) {
        if (tripId == null) {
            return null;
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        if (!trip.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (archiveRepository.existsByTrip_Id(tripId)) {
            throw new BusinessException(ErrorCode.TRIP_ARCHIVE_ALREADY_LINKED);
        }
        return trip;
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

    private CursorPage<ArchiveSummary> toPage(List<Archive> fetched, Long userId, int size) {
        boolean hasNext = fetched.size() > size;
        List<Archive> page = hasNext ? fetched.subList(0, size) : fetched;

        List<ArchiveSummary> summaries = page.stream()
                .map(a -> archiveMapper.toSummary(a, (int) archiveItemRepository.countByArchive(a), userId))
                .toList();

        CursorMeta meta = hasNext
                ? CursorMeta.of(CursorCodec.encode(page.get(page.size() - 1).getId()))
                : CursorMeta.last();
        return new CursorPage<>(summaries, meta);
    }
}
