package com.evergarden.evergardenbackend.place.service;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 3시(KST)에 전날 바뀐 관광지를 반영한다. 자정 근처는 TourAPI의 일일 호출
 * 한도 초기화 시점과 겹칠 수 있어(실전에서 자정 무렵 리셋을 확인했다) 여유를 두고 3시로
 * 잡았다.
 *
 * <p>실패해도 다음 날 스케줄이 끊기지 않는다 — Spring의 기본 스케줄러 오류 처리가
 * 예외를 로그로 남기고 다음 실행은 그대로 유지한다. 여기서도 결과를 로그로 남겨
 * 매일 몇 건이 반영됐는지 운영 중 확인할 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class PlaceIncrementalSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlaceIncrementalSyncScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PlaceIncrementalSyncService placeIncrementalSyncService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncYesterday() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        PlaceIncrementalSyncResult result = placeIncrementalSyncService.syncSince(yesterday);
        log.info("장소 증분 동기화 완료 — 기준일 {}, 생성 {}건, 갱신 {}건, 비표출 {}건 건너뜀, 실패 {}건",
                yesterday, result.created(), result.updated(), result.ignoredDelisted(),
                result.skippedContentIds().size());
    }
}
