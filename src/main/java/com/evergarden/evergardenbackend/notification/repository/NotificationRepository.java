package com.evergarden.evergardenbackend.notification.repository;

import com.evergarden.evergardenbackend.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 받은 알림 최신순, 번호 페이지네이션(NOTI-01 — 명세가 ADR-011의 번호 방식을 명시). */
    Page<Notification> findByReceiver_IdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    /** 배지에 띄울 안 읽은 개수(NOTI-06). */
    long countByReceiver_IdAndReadFalse(Long receiverId);

    /**
     * 안 읽은 알림을 한 번에 모두 읽음으로 바꾼다(NOTI-04). 엔티티를 하나씩 불러
     * {@code markRead()} 호출 후 저장하면, 알림이 쌓일수록 N+1이 부담될 수 있어
     * 처음부터 벌크 {@code UPDATE} 한 문장으로 처리한다({@code UserGardenObjectRepository}의
     * {@code @Modifying} 패턴과 같은 이유).
     *
     * @return 실제로 바뀐 행 수
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.receiver.id = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") Long userId);
}
