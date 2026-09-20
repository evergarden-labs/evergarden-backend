package com.evergarden.evergardenbackend.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.DetailCommonItem;
import com.evergarden.evergardenbackend.place.client.dto.DetailImageItem;
import com.evergarden.evergardenbackend.place.client.dto.DetailIntroItem;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

/** getPlace 라이브 상세조회(PLAN-07 · ADR-061)의 최초 1회 조회·영구 저장·장애 처리를 확인한다. */
class PlaceDetailFetchServiceTest {

    private final TourApiClient tourApiClient = mock(TourApiClient.class);
    private final PlaceDetailFetchService service = new PlaceDetailFetchService(tourApiClient);

    private Place place(String contentId, String contentTypeId) {
        Region region = Region.builder().code("11").level(RegionLevel.SIDO).name("서울")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO).syncedAt(LocalDateTime.now()).build();
        return Place.builder().contentId(contentId).contentTypeId(contentTypeId).title("장소")
                .lat(BigDecimal.ONE).lng(BigDecimal.ONE).region(region).build();
    }

    @Test
    @DisplayName("이미 상세조회를 했으면(detailSyncedAt이 있으면) 다시 부르지 않는다")
    void 이미채워졌으면_다시안부름() {
        Place place = place("c1", "12");
        ReflectionTestUtils.setField(place, "detailSyncedAt", LocalDateTime.now());

        service.ensureDetailFetched(place);

        verify(tourApiClient, never()).fetchDetailCommon(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("관광지(12)는 usetime/restdate로 채운다")
    void 관광지_이용시간() {
        Place place = place("c1", "12");
        given(tourApiClient.fetchDetailCommon("c1")).willReturn(Optional.of(new DetailCommonItem("c1", "멋진 곳")));
        given(tourApiClient.fetchDetailIntro("c1", "12"))
                .willReturn(Optional.of(new DetailIntroItem("c1", "09:00~18:00", "매주 월요일", null, null, null, null)));
        given(tourApiClient.fetchDetailImages("c1"))
                .willReturn(List.of(new DetailImageItem("c1", "http://example.com/1.jpg")));

        service.ensureDetailFetched(place);

        assertThat(place.getOverview()).isEqualTo("멋진 곳");
        assertThat(place.getUseTime()).isEqualTo("09:00~18:00");
        assertThat(place.getRestDate()).isEqualTo("매주 월요일");
        assertThat(place.getImageUrls()).containsExactly("http://example.com/1.jpg");
        assertThat(place.hasDetail()).isTrue();
    }

    @Test
    @DisplayName("음식점(39)은 opentimefood/restdatefood로 채운다")
    void 음식점_이용시간() {
        Place place = place("c2", "39");
        given(tourApiClient.fetchDetailCommon("c2")).willReturn(Optional.empty());
        given(tourApiClient.fetchDetailIntro("c2", "39"))
                .willReturn(Optional.of(new DetailIntroItem("c2", null, null, null, null, "11:00~21:00", "연중무휴")));
        given(tourApiClient.fetchDetailImages("c2")).willReturn(List.of());

        service.ensureDetailFetched(place);

        assertThat(place.getUseTime()).isEqualTo("11:00~21:00");
        assertThat(place.getRestDate()).isEqualTo("연중무휴");
    }

    @Test
    @DisplayName("지원 안 하는 콘텐츠타입(예: 25 여행코스)은 useTime/restDate가 null로 남는다")
    void 미지원타입_null유지() {
        Place place = place("c3", "25");
        given(tourApiClient.fetchDetailCommon("c3")).willReturn(Optional.empty());
        given(tourApiClient.fetchDetailIntro("c3", "25")).willReturn(Optional.empty());
        given(tourApiClient.fetchDetailImages("c3")).willReturn(List.of());

        service.ensureDetailFetched(place);

        assertThat(place.getUseTime()).isNull();
        assertThat(place.getRestDate()).isNull();
        assertThat(place.hasDetail()).isTrue(); // 시도는 했으니 다시 안 부른다
    }

    @Test
    @DisplayName("TourAPI 호출이 실패하면 TOUR_API_UNAVAILABLE")
    void 외부api장애() {
        Place place = place("c1", "12");
        given(tourApiClient.fetchDetailCommon(eq("c1"))).willThrow(new ResourceAccessException("connect timed out"));

        assertThatThrownBy(() -> service.ensureDetailFetched(place))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOUR_API_UNAVAILABLE);
        assertThat(place.hasDetail()).isFalse(); // 실패했으니 다음에 다시 시도할 수 있어야 한다
    }
}
