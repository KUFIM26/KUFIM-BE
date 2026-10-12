package com.example.kufim.stage.repository;

import com.example.kufim.stage.entity.CongestionLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CongestionLogRepository extends JpaRepository<CongestionLog, Long> {
    boolean existsByZoneId(long zoneId);

    @Query("""
            select l from CongestionLog l join fetch l.zone z
            where z.stage.id = :stageId and (:zoneId is null or z.id = :zoneId)
              and l.recordedAt >= :from and l.recordedAt <= :to
            order by l.recordedAt, l.id""")
    List<CongestionLog> history(long stageId, Long zoneId, LocalDateTime from, LocalDateTime to);

    @Query("""
            select l from CongestionLog l join fetch l.zone z join fetch z.stage left join fetch l.recordedBy
            where l.recordedAt >= :from and l.recordedAt <= :to
            order by l.recordedAt, l.id""")
    List<CongestionLog> export(LocalDateTime from, LocalDateTime to);
}
