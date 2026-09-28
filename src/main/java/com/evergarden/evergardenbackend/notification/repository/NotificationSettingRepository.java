package com.evergarden.evergardenbackend.notification.repository;

import com.evergarden.evergardenbackend.notification.entity.NotificationSetting;
import com.evergarden.evergardenbackend.notification.entity.NotificationSettingId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, NotificationSettingId> {

    /**
     * 사용자가 실제로 건드린 설정만 돌아온다(NOTI-03). 손대지 않은 종류는 행 자체가
     * 없다 — {@code NotificationType} 전체 값과 병합하는 건 호출하는 쪽(서비스)이 한다
     * ({@code RegionVisitService.listMyRegions()}와 같은 패턴).
     */
    List<NotificationSetting> findByUser_Id(Long userId);
}
