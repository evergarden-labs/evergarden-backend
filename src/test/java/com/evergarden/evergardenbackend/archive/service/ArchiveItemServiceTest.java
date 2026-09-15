package com.evergarden.evergardenbackend.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.archive.dto.ArchiveCoverRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemUpdateRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemsAddRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveLayoutRequest;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.entity.ArchiveLayout;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaStatus;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import com.evergarden.evergardenbackend.media.service.MediaStorageService;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 아카이브 항목 배치·수정·삭제·일괄 레이아웃·대표 사진(ARCH-06·07·08). */
class ArchiveItemServiceTest {

    private static final Long USER_ID = 1L;

    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final ArchiveItemRepository archiveItemRepository = mock(ArchiveItemRepository.class);
    private final ArchiveCollaboratorRepository collaboratorRepository = mock(ArchiveCollaboratorRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final ArchiveAccessGuard accessGuard = mock(ArchiveAccessGuard.class);
    private final ArchiveMapper archiveMapper =
            new ArchiveMapper(new MediaMapper(mock(MediaStorageService.class)));

    private final ArchiveItemService service = new ArchiveItemService(
            archiveRepository, archiveItemRepository, collaboratorRepository,
            mediaRepository, accessGuard, archiveMapper);

    private final AtomicLong nextId = new AtomicLong(1);

    private Archive archive;

    @BeforeEach
    void setUp() {
        User owner = User.builder().nickname("주인").build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        archive = Archive.builder().owner(owner).title("t").theme(ArchiveTheme.POLAROID).build();
        ReflectionTestUtils.setField(archive, "id", 10L);
        given(archiveRepository.findById(10L)).willReturn(Optional.of(archive));
        given(collaboratorRepository.findByArchive(archive)).willReturn(List.of());
    }

    private Media readyImage(Long uploaderId, LocalDateTime takenAt) {
        User uploader = User.builder().nickname("업로더").build();
        ReflectionTestUtils.setField(uploader, "id", uploaderId);
        Media media = Media.builder().uploader(uploader).type(MediaType.IMAGE).storageKey("key").sizeBytes(1L).build();
        ReflectionTestUtils.setField(media, "id", nextId.getAndIncrement());
        ReflectionTestUtils.setField(media, "status", MediaStatus.READY);
        ReflectionTestUtils.setField(media, "takenAt", takenAt);
        return media;
    }

    private ArchiveItem item(Archive archive, Media media, short sortOrder, ArchiveLayout layout) {
        ArchiveItem item = ArchiveItem.builder()
                .archive(archive).media(media).sortOrder(sortOrder).layout(layout).build();
        ReflectionTestUtils.setField(item, "id", nextId.getAndIncrement());
        return item;
    }

    // ── addItems ─────────────────────────────────────────────────

    @Test
    @DisplayName("layout을 생략하면 기본 배치를 채우고, sortOrder는 기존 개수 뒤에 이어붙인다")
    void 기본_배치와_순서_이어붙이기() {
        Media media = readyImage(USER_ID, null);
        given(mediaRepository.findAllById(List.of(media.getId()))).willReturn(List.of(media));
        given(archiveItemRepository.countByArchive(archive)).willReturn(2L);
        given(archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive)).willReturn(List.of());

        var request = new ArchiveItemsAddRequest(List.of(new ArchiveItemsAddRequest.Item(media.getId(), null, null)));
        var result = service.addItems(USER_ID, 10L, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sortOrder()).isEqualTo((short) 3);
        assertThat(result.get(0).layout()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않거나 아직 READY가 아닌 미디어는 MEDIA_NOT_FOUND")
    void 없거나_준비안된_미디어() {
        given(mediaRepository.findAllById(List.of(42L))).willReturn(List.of());
        var request = new ArchiveItemsAddRequest(List.of(new ArchiveItemsAddRequest.Item(42L, null, null)));

        assertThatThrownBy(() -> service.addItems(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEDIA_NOT_FOUND);
    }

    @Test
    @DisplayName("남이 올린 미디어를 담으려 하면 NOT_RESOURCE_OWNER")
    void 남의_미디어() {
        Media media = readyImage(999L, null);
        given(mediaRepository.findAllById(List.of(media.getId()))).willReturn(List.of(media));
        var request = new ArchiveItemsAddRequest(List.of(new ArchiveItemsAddRequest.Item(media.getId(), null, null)));

        assertThatThrownBy(() -> service.addItems(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("범위를 벗어난 layout은 INVALID_REQUEST")
    void 범위_벗어난_배치() {
        Media media = readyImage(USER_ID, null);
        given(mediaRepository.findAllById(List.of(media.getId()))).willReturn(List.of(media));
        var badLayout = new ArchiveLayout(1.5, 0, 0.3, 0.3, 0);
        var request = new ArchiveItemsAddRequest(
                List.of(new ArchiveItemsAddRequest.Item(media.getId(), badLayout, null)));

        assertThatThrownBy(() -> service.addItems(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("담긴 사진들의 촬영일 최솟값·최댓값으로 여행 기간을 다시 계산한다(ADR-030)")
    void 기간_재계산() {
        Media early = readyImage(USER_ID, LocalDateTime.of(2026, 1, 1, 10, 0));
        Media late = readyImage(USER_ID, LocalDateTime.of(2026, 1, 5, 10, 0));
        Media noExif = readyImage(USER_ID, null);
        given(mediaRepository.findAllById(List.of(early.getId(), late.getId(), noExif.getId())))
                .willReturn(List.of(early, late, noExif));
        given(archiveItemRepository.countByArchive(archive)).willReturn(0L);

        var request = new ArchiveItemsAddRequest(List.of(
                new ArchiveItemsAddRequest.Item(early.getId(), null, null),
                new ArchiveItemsAddRequest.Item(late.getId(), null, null),
                new ArchiveItemsAddRequest.Item(noExif.getId(), null, null)));
        // refreshPeriod가 다시 조회하는 시점엔 방금 추가한 항목들이 반영돼 있어야 한다
        given(archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive)).willAnswer(inv ->
                List.of(item(archive, early, (short) 1, null),
                        item(archive, late, (short) 2, null),
                        item(archive, noExif, (short) 3, null)));

        service.addItems(USER_ID, 10L, request);

        assertThat(archive.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(archive.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    // ── updateItem ───────────────────────────────────────────────

    @Test
    @DisplayName("빈 수정 요청은 INVALID_REQUEST — 아카이브 조회도 안 한다")
    void 빈_수정_요청() {
        var empty = new ArchiveItemUpdateRequest(null, null, null);

        assertThatThrownBy(() -> service.updateItem(USER_ID, 10L, 1L, empty))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("다른 아카이브 소속 항목이면 ARCHIVE_ITEM_NOT_FOUND")
    void 다른_아카이브_항목() {
        Archive other = Archive.builder().owner(archive.getOwner()).title("o").theme(ArchiveTheme.ALBUM).build();
        ReflectionTestUtils.setField(other, "id", 20L);
        ArchiveItem foreignItem = item(other, readyImage(USER_ID, null), (short) 1, null);
        given(archiveItemRepository.findById(foreignItem.getId())).willReturn(Optional.of(foreignItem));

        var request = new ArchiveItemUpdateRequest(null, null, "caption");

        assertThatThrownBy(() -> service.updateItem(USER_ID, 10L, foreignItem.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ARCHIVE_ITEM_NOT_FOUND);
    }

    // ── removeItem ───────────────────────────────────────────────

    @Test
    @DisplayName("대표 사진을 지우면 대표 지정이 풀리고 남은 항목은 순서가 다시 채워진다")
    void 대표사진_삭제와_재배치() {
        ArchiveItem coverItem = item(archive, readyImage(USER_ID, null), (short) 1, null);
        ArchiveItem remaining = item(archive, readyImage(USER_ID, null), (short) 2, ArchiveLayout.of(0, 0, 0.5, 0.5));
        archive.changeCover(coverItem);
        given(archiveItemRepository.findById(coverItem.getId())).willReturn(Optional.of(coverItem));
        given(archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive)).willReturn(List.of(remaining));

        service.removeItem(USER_ID, 10L, coverItem.getId());

        assertThat(archive.getCoverItem()).isNull();
        assertThat(remaining.getSortOrder()).isEqualTo((short) 1);
    }

    // ── replaceLayout ────────────────────────────────────────────

    @Test
    @DisplayName("현재 항목 개수와 다르면 INVALID_REQUEST")
    void 레이아웃_개수_불일치() {
        given(archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive))
                .willReturn(List.of(item(archive, readyImage(USER_ID, null), (short) 1, null)));
        var request = new ArchiveLayoutRequest(List.of());

        assertThatThrownBy(() -> service.replaceLayout(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("모르는 itemId가 섞이면 ARCHIVE_ITEM_NOT_FOUND")
    void 레이아웃에_모르는_itemId() {
        ArchiveItem existing = item(archive, readyImage(USER_ID, null), (short) 1, null);
        given(archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive)).willReturn(List.of(existing));
        var request = new ArchiveLayoutRequest(
                List.of(new ArchiveLayoutRequest.Item(99999L, (short) 1, ArchiveLayout.of(0, 0, 0.5, 0.5))));

        assertThatThrownBy(() -> service.replaceLayout(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ARCHIVE_ITEM_NOT_FOUND);
    }

    // ── setCover ─────────────────────────────────────────────────

    @Test
    @DisplayName("영상은 대표 사진으로 지정할 수 없다")
    void 영상은_대표_불가() {
        User uploader = User.builder().nickname("업로더").build();
        ReflectionTestUtils.setField(uploader, "id", USER_ID);
        Media video = Media.builder().uploader(uploader).type(MediaType.VIDEO).storageKey("k").sizeBytes(1L).build();
        ReflectionTestUtils.setField(video, "id", 500L);
        ArchiveItem videoItem = item(archive, video, (short) 1, null);
        given(archiveItemRepository.findById(videoItem.getId())).willReturn(Optional.of(videoItem));

        assertThatThrownBy(() -> service.setCover(USER_ID, 10L, new ArchiveCoverRequest(videoItem.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
