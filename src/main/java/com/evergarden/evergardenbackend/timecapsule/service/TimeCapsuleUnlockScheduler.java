package com.evergarden.evergardenbackend.timecapsule.service;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 날짜 조건 캡슐을 매일 확인한다(TC-04 설명 중 "날짜 조건에는 오퍼레이션이 필요 없다,
 * 서버 배치가 처리한다" 부분). 자정 직후로 잡으면 스케줄러가 도는 순간과 사용자가
 * "오늘"이라 여기는 날짜가 어긋날 일이 거의 없다 — 위치 판정처럼 즉시성이 중요하지
 * 않아 10분 여유만 뒀다.
 */
@Component
@RequiredArgsConstructor
public class TimeCapsuleUnlockScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeCapsuleUnlockScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TimeCapsuleService timeCapsuleService;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void unlockDueCapsules() {
        LocalDate today = LocalDate.now(KST);
        int count = timeCapsuleService.unlockDueDateCapsules(today);
        log.info("타임캡슐 날짜 해제 배치 완료 — 기준일 {}, {}건 UNLOCKABLE 전환", today, count);
    }
}
