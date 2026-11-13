package com.example.kufim.lostitem.repository;

import com.example.kufim.lostitem.entity.LostItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LostItemRepository extends JpaRepository<LostItem, Long>, JpaSpecificationExecutor<LostItem> {

    /** 관리자 목록 상단의 상태별 건수. 조회 조건과 무관하게 축제 전체를 셉니다. */
    @Query("select l.status, count(l) from LostItem l where l.festival.id = :festivalId and l.deletedAt is null group by l.status")
    List<Object[]> countByStatus(Long festivalId);

    /** 인계 처리처럼 동시에 눌릴 수 있는 변경은 행을 잠그고 상태를 다시 확인합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LostItem l where l.id = :id")
    Optional<LostItem> findByIdForUpdate(Long id);

    /** 사진 정리에서 "쓰이고 있는 키" 목록으로 씁니다. */
    @Query("select l.imageKey from LostItem l where l.imageKey is not null")
    List<String> findUsedImageKeys();

    /** 개인정보 파기 대상: 인계가 끝나고 기준 시각보다 오래된 건. */
    @Query("select l from LostItem l where l.status = com.example.kufim.lostitem.entity.LostItemStatus.RETURNED "
            + "and l.returnedAt < :before and l.receiverPhone is not null")
    List<LostItem> findPurgeTargets(java.time.LocalDateTime before);
}
