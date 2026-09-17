package com.evergarden.evergardenbackend.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.place.client.dto.AreaBasedItem;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceUpsertServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceUpsertService service = new PlaceUpsertService(placeRepository);

    private Region region() {
        return Region.builder().code("11110").level(RegionLevel.SIGUNGU).name("종로구")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO).syncedAt(LocalDateTime.now()).build();
    }

    private AreaBasedItem item() {
        return new AreaBasedItem("c1", "12", "경복궁", "서울 종로구", "02-000-0000",
                "126.97", "37.57", "http://example.com/t.jpg", "11", "110");
    }

    @Test
    @DisplayName("없던 contentId면 새로 만든다")
    void 신규생성() {
        given(placeRepository.findByContentId("c1")).willReturn(Optional.empty());

        boolean isNew = service.upsertOne(item(), region(), new BigDecimal("37.57"), new BigDecimal("126.97"),
                LocalDateTime.now());

        assertThat(isNew).isTrue();
        verify(placeRepository).save(any(Place.class));
    }

    @Test
    @DisplayName("있던 contentId면 새로 만들지 않고 값만 갱신한다")
    void 기존갱신() {
        Place existing = Place.builder()
                .contentId("c1").contentTypeId("12").title("옛이름")
                .lat(BigDecimal.ZERO).lng(BigDecimal.ZERO).region(region())
                .syncedAt(LocalDateTime.now().minusDays(1)).build();
        given(placeRepository.findByContentId("c1")).willReturn(Optional.of(existing));

        boolean isNew = service.upsertOne(item(), region(), new BigDecimal("37.57"), new BigDecimal("126.97"),
                LocalDateTime.now());

        assertThat(isNew).isFalse();
        assertThat(existing.getTitle()).isEqualTo("경복궁");
        verify(placeRepository, never()).save(any());
    }

    @Test
    @DisplayName("갱신할 때 이미 있던 overview·useTime·restDate는 지우지 않는다")
    void 갱신시_소개정보_보존() {
        Place existing = Place.builder()
                .contentId("c1").contentTypeId("12").title("옛이름")
                .lat(BigDecimal.ZERO).lng(BigDecimal.ZERO).region(region())
                .overview("기존 개요").useTime("기존 이용시간").restDate("기존 휴무일")
                .syncedAt(LocalDateTime.now().minusDays(1)).build();
        given(placeRepository.findByContentId("c1")).willReturn(Optional.of(existing));

        service.upsertOne(item(), region(), new BigDecimal("37.57"), new BigDecimal("126.97"), LocalDateTime.now());

        assertThat(existing.getOverview()).isEqualTo("기존 개요");
        assertThat(existing.getUseTime()).isEqualTo("기존 이용시간");
        assertThat(existing.getRestDate()).isEqualTo("기존 휴무일");
    }
}
