package com.example.kufim.notice.repository;

import com.example.kufim.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;



public interface NoticeRepository extends JpaRepository<Notice, Long> {
    java.util.List<Notice> findAllByDeletedFalseAndPublishedAtLessThanEqualOrderByPublishedAtDesc(java.time.LocalDateTime time);
    java.util.Optional<Notice> findByIdAndDeletedFalseAndPublishedAtLessThanEqual(Long id, java.time.LocalDateTime time);
}
