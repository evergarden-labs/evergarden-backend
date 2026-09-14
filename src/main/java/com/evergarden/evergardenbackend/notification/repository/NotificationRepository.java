package com.evergarden.evergardenbackend.notification.repository;

import com.evergarden.evergardenbackend.notification.entity.Notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
