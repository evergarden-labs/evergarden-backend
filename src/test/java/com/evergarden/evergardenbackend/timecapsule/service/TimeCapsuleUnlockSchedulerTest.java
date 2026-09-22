package com.evergarden.evergardenbackend.timecapsule.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 스케줄러가 KST 기준 오늘 날짜로 서비스를 부르는지 확인한다. */
class TimeCapsuleUnlockSchedulerTest {

    private final TimeCapsuleService service = mock(TimeCapsuleService.class);
    private final TimeCapsuleUnlockScheduler scheduler = new TimeCapsuleUnlockScheduler(service);

    @Test
    @DisplayName("KST 기준 오늘 날짜로 날짜 해제 배치를 부른다")
    void 오늘날짜로_호출() {
        given(service.unlockDueDateCapsules(any())).willReturn(0);
        LocalDate expected = LocalDate.now(ZoneId.of("Asia/Seoul"));

        scheduler.unlockDueCapsules();

        verify(service).unlockDueDateCapsules(expected);
    }
}
