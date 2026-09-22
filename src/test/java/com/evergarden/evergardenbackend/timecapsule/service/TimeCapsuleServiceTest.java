package com.evergarden.evergardenbackend.timecapsule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.util.CursorCodec;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaStatus;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleCreateRequest;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleDetail;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleSummary;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMedia;
import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleMediaRepository;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 타임캡슐 생성·조회(TC-01·03·06)의 해제 조건 검증·미디어 확인·내용 노출 규칙을 확인한다. */
class TimeCapsuleServiceTest {

    private static final Long USER_ID = 1L;

    private final TimeCapsuleRepository timeCapsuleRepository = mock(TimeCapsuleRepository.class);
    private final TimeCapsuleMediaRepository timeCapsuleMediaRepository = mock(TimeCapsuleMediaRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TimeCapsuleAccessGuard accessGuard = mock(TimeCapsuleAccessGuard.class);
    private final TimeCapsuleMapper timeCapsuleMapper = new TimeCapsuleMapper();
    private final MediaMapper mediaMapper = mock(MediaMapper.class);

    private final TimeCapsuleService timeCapsuleService = new TimeCapsuleService(
            timeCapsuleRepository, timeCapsuleMediaRepository, mediaRepository, userRepository,
            accessGuard, timeCapsuleMapper, mediaMapper);

    private User author;

    @BeforeEach
    void setUp() {
        author = user(USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(author);
    }

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Media readyMedia(Long id, Long uploaderId) {
        Media media = Media.builder().uploader(user(uploaderId)).type(MediaType.IMAGE)
                .storageKey("key" + id).sizeBytes(1L).build();
        ReflectionTestUtils.setField(media, "id", id);
        ReflectionTestUtils.setField(media, "status", MediaStatus.READY);
        return media;
    }

    private TimeCapsuleCreateRequest dateRequest(LocalDate unlockDate, List<Long> mediaIds) {
        return new TimeCapsuleCreateRequest("제주 여행 기억", "그날의 기억", UnlockType.DATE,
                unlockDate, null, null, null, null, mediaIds);
    }

    private TimeCapsuleCreateRequest locationRequest(Double lat, Double lng, Integer radius) {
        return new TimeCapsuleCreateRequest("제주 여행 기억", "그날의 기억", UnlockType.LOCATION,
                null, lat, lng, radius, "한라산", null);
    }

    // ── 해제 조건 검증 ───────────────────────────────────────

    @Test
    @DisplayName("DATE인데 unlockDate가 없으면 INVALID_UNLOCK_CONDITION")
    void DATE_날짜없음() {
        TimeCapsuleCreateRequest request = dateRequest(null, null);

        assertThatThrownBy(() -> timeCapsuleService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_UNLOCK_CONDITION);
        verify(timeCapsuleRepository, never()).save(any());
    }

    @Test
    @DisplayName("DATE인데 과거 날짜면 INVALID_REQUEST")
    void DATE_과거날짜() {
        TimeCapsuleCreateRequest request = dateRequest(LocalDate.now().minusDays(1), null);

        assertThatThrownBy(() -> timeCapsuleService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("DATE인데 오늘 날짜면 INVALID_REQUEST — 오늘보다 뒤여야 한다")
    void DATE_오늘날짜() {
        TimeCapsuleCreateRequest request = dateRequest(LocalDate.now(), null);

        assertThatThrownBy(() -> timeCapsuleService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("LOCATION인데 좌표·반경 중 하나라도 없으면 INVALID_UNLOCK_CONDITION")
    void LOCATION_필드누락() {
        TimeCapsuleCreateRequest request = locationRequest(37.5, null, 100);

        assertThatThrownBy(() -> timeCapsuleService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_UNLOCK_CONDITION);
    }

    // ── 미디어 검증 ──────────────────────────────────────────

    @Test
    @DisplayName("업로드 미완료(READY 아님) 미디어를 담으면 MEDIA_NOT_FOUND")
    void 미디어_READY아님() {
        Media pending = Media.builder().uploader(author).type(MediaType.IMAGE)
                .storageKey("key").sizeBytes(1L).build();
        ReflectionTestUtils.setField(pending, "id", 10L);
        given(mediaRepository.findAllById(List.of(10L))).willReturn(List.of(pending));

        TimeCapsuleCreateRequest request = dateRequest(LocalDate.now().plusDays(30), List.of(10L));

        assertThatThrownBy(() -> timeCapsuleService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
    }

    @Test
    @DisplayName("남이 올린 미디어를 담으면 403이 아니라 MEDIA_NOT_FOUND — 명세에 403이 없다")
    void 미디어_남의것() {
        Media othersMedia = readyMedia(10L, 2L);
        given(mediaRepository.findAllById(List.of(10L))).willReturn(List.of(othersMedia));

        TimeCapsuleCreateRequest request = dateRequest(LocalDate.now().plusDays(30), List.of(10L));

        assertThatThrownBy(() -> timeCapsuleService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
    }

    // ── 정상 생성 ────────────────────────────────────────────

    @Test
    @DisplayName("정상 생성은 SEALED 상태이고, 만든 직후에도 content·media는 안 담긴다")
    void 정상생성_DATE() {
        TimeCapsuleCreateRequest request = dateRequest(LocalDate.now().plusDays(30), null);

        TimeCapsuleDetail result = timeCapsuleService.create(USER_ID, request);

        assertThat(result.status().name()).isEqualTo("SEALED");
        assertThat(result.content()).isNull();
        assertThat(result.media()).isEmpty();
        assertThat(result.thumbnailUrl()).isNull();
        assertThat(result.unlockCondition().satisfied()).isFalse();
        verify(timeCapsuleRepository).save(any());
    }

    @Test
    @DisplayName("정상 생성(LOCATION)은 unlockCondition에 좌표·반경·장소이름을 담는다")
    void 정상생성_LOCATION() {
        TimeCapsuleCreateRequest request = locationRequest(37.5, 126.9, 100);

        TimeCapsuleDetail result = timeCapsuleService.create(USER_ID, request);

        assertThat(result.unlockCondition().type()).isEqualTo(UnlockType.LOCATION);
        assertThat(result.unlockCondition().lat()).isEqualTo(37.5);
        assertThat(result.unlockCondition().radiusMeters()).isEqualTo(100);
        assertThat(result.unlockCondition().placeName()).isEqualTo("한라산");
    }

    @Test
    @DisplayName("mediaIds를 담으면 순서대로 TimeCapsuleMedia가 만들어진다")
    void 정상생성_미디어첨부() {
        Media media1 = readyMedia(10L, USER_ID);
        Media media2 = readyMedia(11L, USER_ID);
        given(mediaRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(media1, media2));

        TimeCapsuleCreateRequest request = dateRequest(LocalDate.now().plusDays(30), List.of(10L, 11L));
        timeCapsuleService.create(USER_ID, request);

        ArgumentCaptor<List<TimeCapsuleMedia>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeCapsuleMediaRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).getSortOrder()).isEqualTo((short) 1);
        assertThat(captor.getValue().get(1).getSortOrder()).isEqualTo((short) 2);
    }

    // ── 조회(TC-03·06) ───────────────────────────────────────

    private TimeCapsule capsule(Long id) {
        TimeCapsule capsule = TimeCapsule.sealUntilDate(author, "제목", "내용", LocalDate.now().plusDays(10));
        ReflectionTestUtils.setField(capsule, "id", id);
        return capsule;
    }

    @Test
    @DisplayName("없는 캡슐을 조회하면 TIME_CAPSULE_NOT_FOUND")
    void 조회_없는캡슐() {
        given(timeCapsuleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> timeCapsuleService.get(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TIME_CAPSULE_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 캡슐을 조회하면 403 — 접근가드에 위임한다")
    void 조회_남의캡슐() {
        TimeCapsule capsule = capsule(5L);
        given(timeCapsuleRepository.findById(5L)).willReturn(Optional.of(capsule));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(accessGuard).checkOwner(capsule, USER_ID);

        assertThatThrownBy(() -> timeCapsuleService.get(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("봉인 상태면 content·media·thumbnailUrl이 전부 비어 있다 — 내용이 새면 안 된다")
    void 조회_봉인상태() {
        TimeCapsule capsule = capsule(5L);
        given(timeCapsuleRepository.findById(5L)).willReturn(Optional.of(capsule));

        TimeCapsuleDetail result = timeCapsuleService.get(USER_ID, 5L);

        assertThat(result.content()).isNull();
        assertThat(result.media()).isEmpty();
        assertThat(result.thumbnailUrl()).isNull();
        org.mockito.Mockito.verifyNoInteractions(mediaMapper);
    }

    @Test
    @DisplayName("열어본 캡슐은 content·media·thumbnailUrl이 채워진다")
    void 조회_열어본캡슐() {
        TimeCapsule capsule = capsule(5L);
        capsule.open(java.time.LocalDateTime.now());
        Media media = readyMedia(10L, USER_ID);
        TimeCapsuleMedia item = new TimeCapsuleMedia(capsule, media, (short) 1);
        given(timeCapsuleRepository.findById(5L)).willReturn(Optional.of(capsule));
        given(timeCapsuleMediaRepository.findByCapsuleOrderBySortOrderAsc(capsule)).willReturn(List.of(item));
        given(mediaMapper.toResponse(media)).willReturn(new MediaResponse(
                10L, MediaStatus.READY, MediaType.IMAGE, "http://example.com/1.jpg", null,
                100, 100, null, 1L, null, null, null, null));

        TimeCapsuleDetail result = timeCapsuleService.get(USER_ID, 5L);

        assertThat(result.content()).isEqualTo("내용");
        assertThat(result.media()).hasSize(1);
        assertThat(result.thumbnailUrl()).isEqualTo("http://example.com/1.jpg");
    }

    // ── 목록(TC-02) ──────────────────────────────────────────

    @Test
    @DisplayName("목록이 비어 있으면 hasNext=false, nextCursor=null")
    void 목록_없음() {
        given(timeCapsuleRepository.findAllByOwner(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.isNull(), any())).willReturn(List.of());

        CursorPage<TimeCapsuleSummary> page = timeCapsuleService.list(USER_ID, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.meta().hasNext()).isFalse();
        assertThat(page.meta().nextCursor()).isNull();
    }

    @Test
    @DisplayName("size+1개가 돌아오면 hasNext=true이고, 마지막 항목의 id로 다음 커서를 만든다")
    void 목록_다음페이지있음() {
        List<TimeCapsule> twoOfThree = List.of(capsule(3L), capsule(2L), capsule(1L));
        given(timeCapsuleRepository.findAllByOwner(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 3))))
                .willReturn(twoOfThree);

        CursorPage<TimeCapsuleSummary> page = timeCapsuleService.list(USER_ID, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.meta().hasNext()).isTrue();
        assertThat(page.meta().nextCursor()).isEqualTo(CursorCodec.encode(2L));
    }

    @Test
    @DisplayName("봉인 상태 항목은 목록에서도 thumbnailUrl이 null이다 — 미디어를 조회하지 않는다")
    void 목록_봉인상태_썸네일없음() {
        TimeCapsule sealed = capsule(1L);
        given(timeCapsuleRepository.findAllByOwner(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.isNull(), any())).willReturn(List.of(sealed));

        CursorPage<TimeCapsuleSummary> page = timeCapsuleService.list(USER_ID, null, 20);

        assertThat(page.items().get(0).thumbnailUrl()).isNull();
        org.mockito.Mockito.verifyNoInteractions(timeCapsuleMediaRepository);
    }

    @Test
    @DisplayName("열어본 항목은 목록에서 첫 미디어로 thumbnailUrl을 채운다")
    void 목록_열어본항목_썸네일채움() {
        TimeCapsule opened = capsule(1L);
        opened.open(LocalDateTime.now());
        Media media = readyMedia(10L, USER_ID);
        TimeCapsuleMedia item = new TimeCapsuleMedia(opened, media, (short) 1);
        given(timeCapsuleRepository.findAllByOwner(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.isNull(), any())).willReturn(List.of(opened));
        given(timeCapsuleMediaRepository.findByCapsuleOrderBySortOrderAsc(opened)).willReturn(List.of(item));
        given(mediaMapper.toResponse(media)).willReturn(new MediaResponse(
                10L, MediaStatus.READY, MediaType.IMAGE, "http://example.com/1.jpg", null,
                100, 100, null, 1L, null, null, null, null));

        CursorPage<TimeCapsuleSummary> page = timeCapsuleService.list(USER_ID, null, 20);

        assertThat(page.items().get(0).thumbnailUrl()).isEqualTo("http://example.com/1.jpg");
    }

    // ── 열어본 목록(TC-06) ────────────────────────────────────

    @Test
    @DisplayName("열어본 목록이 비어 있으면 hasNext=false, nextCursor=null")
    void 열어본목록_없음() {
        given(timeCapsuleRepository.findOpenedByOwner(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), any()))
                .willReturn(List.of());

        CursorPage<TimeCapsuleSummary> page = timeCapsuleService.listOpened(USER_ID, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.meta().hasNext()).isFalse();
    }

    @Test
    @DisplayName("열어본 목록의 커서는 왕복한다 — (openedAt, id) 둘 다 다음 요청에 그대로 실려간다")
    void 열어본목록_커서왕복() {
        TimeCapsule opened1 = capsule(2L);
        opened1.open(LocalDateTime.of(2026, 1, 2, 10, 0));
        TimeCapsule opened2 = capsule(1L);
        opened2.open(LocalDateTime.of(2026, 1, 1, 10, 0));
        given(timeCapsuleRepository.findOpenedByOwner(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 2))))
                .willReturn(List.of(opened1, opened2));

        CursorPage<TimeCapsuleSummary> firstPage = timeCapsuleService.listOpened(USER_ID, null, 1);
        assertThat(firstPage.meta().hasNext()).isTrue();
        String nextCursor = firstPage.meta().nextCursor();

        ArgumentCaptor<LocalDateTime> openedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        given(timeCapsuleRepository.findOpenedByOwner(org.mockito.ArgumentMatchers.eq(USER_ID),
                openedAtCaptor.capture(), idCaptor.capture(), any())).willReturn(List.of(opened2));

        timeCapsuleService.listOpened(USER_ID, nextCursor, 1);

        assertThat(openedAtCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
        assertThat(idCaptor.getValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("깨진 커서 문자열이면 INVALID_REQUEST")
    void 열어본목록_잘못된커서() {
        assertThatThrownBy(() -> timeCapsuleService.listOpened(USER_ID, "not-a-valid-cursor!!", 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    // ── 삭제(TC-07) ──────────────────────────────────────────

    @Test
    @DisplayName("없는 캡슐을 삭제하면 TIME_CAPSULE_NOT_FOUND")
    void 삭제_없는캡슐() {
        given(timeCapsuleRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> timeCapsuleService.delete(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TIME_CAPSULE_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 캡슐을 삭제하면 403 — 접근가드에 위임한다")
    void 삭제_남의캡슐() {
        TimeCapsule capsule = capsule(5L);
        given(timeCapsuleRepository.findById(5L)).willReturn(Optional.of(capsule));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(accessGuard).checkOwner(capsule, USER_ID);

        assertThatThrownBy(() -> timeCapsuleService.delete(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("봉인 상태에서도 삭제할 수 있고, 행을 실제로 지운다")
    void 삭제_정상() {
        TimeCapsule capsule = capsule(5L);
        given(timeCapsuleRepository.findById(5L)).willReturn(Optional.of(capsule));

        timeCapsuleService.delete(USER_ID, 5L);

        verify(timeCapsuleRepository).delete(capsule);
    }
}
