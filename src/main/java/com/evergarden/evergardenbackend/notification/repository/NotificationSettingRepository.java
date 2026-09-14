package com.evergarden.evergardenbackend.notification.repository;

import com.evergarden.evergardenbackend.notification.entity.NotificationSetting;
import com.evergarden.evergardenbackend.notification.entity.NotificationSettingId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, NotificationSettingId> {
}
