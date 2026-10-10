package com.example.kufim.stage.repository;

import com.example.kufim.stage.entity.StageZone;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StageZoneRepository extends JpaRepository<StageZone, Long> {
    @Query("select z from StageZone z left join fetch z.latestLog where z.stage.id = :stageId order by z.displayOrder, z.id")
    List<StageZone> findByStage(long stageId);

    /** 공개 무대 목록용: 활성 구역과 최신 혼잡도를 한 번에 읽습니다. */
    @Query("select z from StageZone z left join fetch z.latestLog where z.active = true order by z.displayOrder, z.id")
    List<StageZone> findActiveWithLatest();

    long countByStageIdAndActiveTrue(long stageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from StageZone z where z.id = :id")
    Optional<StageZone> lock(long id);
}
