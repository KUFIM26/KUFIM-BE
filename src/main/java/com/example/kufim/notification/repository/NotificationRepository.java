package com.example.kufim.notification.repository;

import com.example.kufim.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;



public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    java.util.Optional<Notification> lockById(@Param("id") Long id);
    @Query("select n from Notification n left join n.notice notice where (n.type = com.example.kufim.notification.entity.NotificationType.NOTICE and notice.deleted = false and notice.publishedAt <= :now) or n.recipientClientToken = :token order by n.id desc")
    Page<Notification> visible(@Param("token") String token, @Param("now") java.time.LocalDateTime now, Pageable pageable);
    @Query("select n from Notification n left join n.notice notice where ((n.type = com.example.kufim.notification.entity.NotificationType.NOTICE and notice.deleted = false and notice.publishedAt <= :now) or n.recipientClientToken = :token) and n.id <= :id order by n.id")
    java.util.List<Notification> visibleThrough(@Param("token") String token, @Param("now") java.time.LocalDateTime now, @Param("id") long id);
    @Query("select count(n) from Notification n left join n.notice notice where ((n.type = com.example.kufim.notification.entity.NotificationType.NOTICE and notice.deleted = false and notice.publishedAt <= :now) or n.recipientClientToken = :token) and not exists (select r.id from NotificationRead r where r.notification = n and r.clientToken = :token)")
    long unreadCount(@Param("token") String token, @Param("now") java.time.LocalDateTime now);
    java.util.Optional<Notification> findByNoticeId(Long noticeId);
    java.util.List<Notification> findByNoticeIdIn(java.util.Collection<Long> noticeIds);
}
