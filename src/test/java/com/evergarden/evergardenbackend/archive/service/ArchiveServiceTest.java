package com.evergarden.evergardenbackend.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.archive.dto.ArchiveCreateRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.archive.dto.ArchiveUpdateRequest;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.util.CursorCodec;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** 아카이브 CRUD의 검증 순서·오류 코드를 확인한다(ARCH-01·02·03·04·05·09). */
class ArchiveServiceTest {

    private static final Long USER_ID = 1L;

    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final ArchiveItemRepository archiveItemRepository = mock(ArchiveItemRepository.class);
    private final ArchiveCollaboratorRepository collaboratorRepository = mock(ArchiveCollaboratorRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ArchiveAccessGuard accessGuard = mock(ArchiveAccessGuard.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final ArchiveService archiveService = new ArchiveService(
            archiveRepository, archiveItemRepository, collaboratorRepository,
            userRepository, tripRepository, accessGuard, archiveMapper, eventPublisher);

    @BeforeEach
    void setUp() {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(archiveItemRepository.findByArchiveOrderBySortOrderAsc(any())).willReturn(List.of());
        given(collaboratorRepository.findByArchive(any())).willReturn(List.of());
    }

    private ArchiveCreateRequest createRequest(Long tripId) {
        return new ArchiveCreateRequest("제주도 여행", ArchiveTheme.POLAROID, "#FF5733", tripId);
    }

    private Trip tripOwnedBy(Long ownerId) {
        User owner = User.builder().nickname("트립주인").build();
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Trip trip = Trip.builder().owner(owner).title("여행").startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3)).build();
        ReflectionTestUtils.setField(trip, "id", 100L);
        return trip;
    }

    // ── create ───────────────────────────────────────────────────

    @Test
    @DisplayName("일정 없이 만들면 소유자만 저장하고 공동편집자로 owner를 등록한다")
    void 일정_없이_생성() {
        archiveService.create(USER_ID, createRequest(null));

        verify(archiveRepository).save(any(Archive.class));
        verify(collaboratorRepository).save(any());
        verify(tripRepository, never()).findById(any());
    }

    @Test
    @DisplayName("없는 일정이면 TRIP_NOT_FOUND")
    void 없는_일정_연결() {
        given(tripRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> archiveService.create(USER_ID, createRequest(100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 일정이면 NOT_RESOURCE_OWNER")
    void 남의_일정_연결() {
        given(tripRepository.findById(100L)).willReturn(Optional.of(tripOwnedBy(999L)));

        assertThatThrownBy(() -> archiveService.create(USER_ID, createRequest(100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("이미 다른 아카이브와 연결된 일정이면 TRIP_ARCHIVE_ALREADY_LINKED")
    void 이미_연결된_일정() {
        given(tripRepository.findById(100L)).willReturn(Optional.of(tripOwnedBy(USER_ID)));
        given(archiveRepository.existsByTrip_Id(100L)).willReturn(true);

        assertThatThrownBy(() -> archiveService.create(USER_ID, createRequest(100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRIP_ARCHIVE_ALREADY_LINKED);
    }

    // ── get ──────────────────────────────────────────────────────

    @Test
    @DisplayName("없는 아카이브 조회는 ARCHIVE_NOT_FOUND")
    void 없는_아카이브_조회() {
        given(archiveRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> archiveService.get(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ARCHIVE_NOT_FOUND);
    }

    @Test
    @DisplayName("조회 권한 판단은 ArchiveAccessGuard에 위임한다")
    void 조회_권한_위임() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(accessGuard).checkViewable(archive, USER_ID);

        assertThatThrownBy(() -> archiveService.get(USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    // ── update ───────────────────────────────────────────────────

    @Test
    @DisplayName("보낸 필드가 하나도 없으면 INVALID_REQUEST — 권한도 안 본다")
    void 빈_수정_요청() {
        ArchiveUpdateRequest empty = new ArchiveUpdateRequest(null, null, null);

        assertThatThrownBy(() -> archiveService.update(USER_ID, 1L, empty))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(archiveRepository, never()).findById(any());
    }

    @Test
    @DisplayName("정상 수정은 편집 권한을 확인한 뒤 엔티티를 갱신한다")
    void 정상_수정() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));

        archiveService.update(USER_ID, 1L, new ArchiveUpdateRequest("새 이름", null, null));

        verify(accessGuard).checkEditable(archive, USER_ID);
        assertThat(archive.getTitle()).isEqualTo("새 이름");
    }

    @Test
    @DisplayName("수정하면 archive.updated 이벤트를 보낸 필드만 담아 발행한다")
    void 수정_실시간_이벤트() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));

        archiveService.update(USER_ID, 1L, new ArchiveUpdateRequest("새 이름", null, null));

        org.mockito.ArgumentCaptor<com.evergarden.evergardenbackend.archive.event.ArchiveRealtimeEvent> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.evergarden.evergardenbackend.archive.event.ArchiveRealtimeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.type()).isEqualTo("archive.updated");
        assertThat(event.archiveId()).isEqualTo(1L);
        assertThat(event.payload()).isEqualTo(java.util.Map.of("title", "새 이름"));
    }

    // ── delete ───────────────────────────────────────────────────

    @Test
    @DisplayName("삭제는 소유자 전용 검사를 거친다")
    void 삭제는_소유자_검사() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));

        archiveService.delete(USER_ID, 1L);

        verify(accessGuard).checkOwner(archive, USER_ID);
        verify(archiveRepository).delete(archive);
    }

    // ── linkTrip / unlinkTrip (ARCH-17) ─────────────────────────────

    @Test
    @DisplayName("없는 일정이면 TRIP_NOT_FOUND")
    void 연결_없는_일정() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));
        given(tripRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> archiveService.linkTrip(USER_ID, 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 일정이면 NOT_RESOURCE_OWNER")
    void 연결_남의_일정() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));
        given(tripRepository.findById(100L)).willReturn(Optional.of(tripOwnedBy(999L)));

        assertThatThrownBy(() -> archiveService.linkTrip(USER_ID, 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("이미 다른 아카이브에 연결된 일정이면 TRIP_ARCHIVE_ALREADY_LINKED")
    void 연결_이미_다른_아카이브에_연결됨() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));
        given(tripRepository.findById(100L)).willReturn(Optional.of(tripOwnedBy(USER_ID)));
        given(archiveRepository.existsByTrip_Id(100L)).willReturn(true);

        assertThatThrownBy(() -> archiveService.linkTrip(USER_ID, 1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TRIP_ARCHIVE_ALREADY_LINKED);
    }

    @Test
    @DisplayName("지금 이 아카이브에 이미 연결된 일정을 다시 보내면 그대로 통과한다")
    void 연결_같은_일정_재연결() {
        Archive archive = archiveOwnedBy(USER_ID);
        Trip trip = tripOwnedBy(USER_ID);
        ReflectionTestUtils.setField(archive, "trip", trip);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));
        given(tripRepository.findById(trip.getId())).willReturn(Optional.of(trip));
        given(archiveRepository.existsByTrip_Id(trip.getId())).willReturn(true);

        archiveService.linkTrip(USER_ID, 1L, trip.getId());

        assertThat(archive.getTrip()).isEqualTo(trip);
    }

    @Test
    @DisplayName("정상 연결은 소유자 전용 검사를 거치고 트립을 바꾼다")
    void 연결_정상() {
        Archive archive = archiveOwnedBy(USER_ID);
        Trip trip = tripOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));
        given(tripRepository.findById(trip.getId())).willReturn(Optional.of(trip));

        archiveService.linkTrip(USER_ID, 1L, trip.getId());

        verify(accessGuard).checkOwner(archive, USER_ID);
        assertThat(archive.getTrip()).isEqualTo(trip);
    }

    @Test
    @DisplayName("연결 해제는 연결이 없어도 그냥 성공한다")
    void 연결해제_정상() {
        Archive archive = archiveOwnedBy(USER_ID);
        given(archiveRepository.findById(1L)).willReturn(Optional.of(archive));

        archiveService.unlinkTrip(USER_ID, 1L);

        verify(accessGuard).checkOwner(archive, USER_ID);
        assertThat(archive.getTrip()).isNull();
    }

    // ── list / search ────────────────────────────────────────────

    @Test
    @DisplayName("size보다 많이 돌아오면 hasNext=true, 마지막 요소는 잘라낸다")
    void 목록_다음페이지_있음() {
        Archive a1 = archiveOwnedBy(USER_ID);
        Archive a2 = archiveOwnedBy(USER_ID);
        ReflectionTestUtils.setField(a1, "id", 20L);
        ReflectionTestUtils.setField(a2, "id", 19L);
        given(archiveRepository.findAccessible(eq(USER_ID), eq(null), any(Pageable.class)))
                .willReturn(List.of(a1, a2));
        given(archiveMapper.toSummary(any(Archive.class), anyInt(), eq(USER_ID)))
                .willReturn(mock(ArchiveSummary.class));

        CursorPage<ArchiveSummary> page = archiveService.list(USER_ID, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.meta().hasNext()).isTrue();
        assertThat(page.meta().nextCursor()).isEqualTo(CursorCodec.encode(20L));
    }

    @Test
    @DisplayName("검색 조건이 하나도 없으면 INVALID_REQUEST")
    void 검색_조건_없음() {
        assertThatThrownBy(() -> archiveService.search(USER_ID, null, null, null, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("from이 to보다 늦으면 INVALID_DATE_RANGE")
    void 잘못된_기간() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> archiveService.search(USER_ID, null, from, to, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }

    private Archive archiveOwnedBy(Long ownerId) {
        User owner = User.builder().nickname("주인").build();
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Archive archive = Archive.builder().owner(owner).title("t").theme(ArchiveTheme.POLAROID).build();
        ReflectionTestUtils.setField(archive, "id", 1L);
        return archive;
    }
}
