package com.example.kufim.report.repository;

import com.example.kufim.report.entity.Report;
import com.example.kufim.report.entity.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 같은 기기에서 등록한 내 신고 목록, 최신순. */
    List<Report> findByClientTokenOrderByCreatedAtDescIdDesc(String clientToken);

    Page<Report> findByFestivalIdOrderByCreatedAtDescIdDesc(Long festivalId, Pageable pageable);

    Page<Report> findByFestivalIdAndStatusOrderByCreatedAtDescIdDesc(
            Long festivalId, ReportStatus status, Pageable pageable);

    /** 관리자 목록 상단의 상태별 건수. */
    @Query("select r.status, count(r) from Report r where r.festival.id = :festivalId group by r.status")
    List<Object[]> countByStatus(Long festivalId);

    /**
     * 상태를 바꾸기 전에 행을 잠급니다. 두 관리자가 동시에 상세를 열어도
     * 접수 전이와 알림이 한 번만 일어나도록 하기 위한 것입니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> findByIdForUpdate(Long id);

    /** 개인정보 파기 대상: 종결된 지 기준 시각이 지난 신고. */
    @Query("""
            select r from Report r
             where r.status in (com.example.kufim.report.entity.ReportStatus.COMPLETED,
                                com.example.kufim.report.entity.ReportStatus.CANCELED)
               and r.completedAt < :beforeAt
               and r.phoneNumber <> '00000000000'
            """)
    List<Report> findPurgeTargets(LocalDateTime beforeAt);
}
