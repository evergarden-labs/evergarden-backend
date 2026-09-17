package com.evergarden.evergardenbackend.place.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 스케줄러가 "어제" 날짜를 KST 기준으로 계산해 서비스에 넘기는지 확인한다. */
class PlaceIncrementalSyncSchedulerTest {

    private final PlaceIncrementalSyncService service = mock(PlaceIncrementalSyncService.class);
    private final PlaceIncrementalSyncScheduler scheduler = new PlaceIncrementalSyncScheduler(service);

    @Test
    @DisplayName("KST 기준 어제 날짜로 동기화를 부른다")
    void 어제날짜로_호출() {
        given(service.syncSince(any())).willReturn(new PlaceIncrementalSyncResult(0, 0, 0, List.of()));
        LocalDate expected = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);

        scheduler.syncYesterday();

        verify(service).syncSince(expected);
    }
}
