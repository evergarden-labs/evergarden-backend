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
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaStatus;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleCreateRequest;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleDetail;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMedia;
import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleMediaRepository;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 타임캡슐 생성(TC-01)의 해제 조건 검증·미디어 확인 순서를 확인한다. */
class TimeCapsuleServiceTest {

    private static final Long USER_ID = 1L;

    private final TimeCapsuleRepository timeCapsuleRepository = mock(TimeCapsuleRepository.class);
    private final TimeCapsuleMediaRepository timeCapsuleMediaRepository = mock(TimeCapsuleMediaRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TimeCapsuleMapper timeCapsuleMapper = new TimeCapsuleMapper();

    private final TimeCapsuleService timeCapsuleService = new TimeCapsuleService(
            timeCapsuleRepository, timeCapsuleMediaRepository, mediaRepository, userRepository, timeCapsuleMapper);

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
}
