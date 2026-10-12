package com.example.kufim.notification.repository;

import com.example.kufim.notification.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;



public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {
    java.util.Optional<NotificationRead> findByNotificationIdAndClientToken(Long notificationId, String clientToken);
    long countByClientToken(String clientToken);
    java.util.List<NotificationRead> findByClientTokenAndNotificationIdIn(String clientToken, java.util.Collection<Long> notificationIds);
}
