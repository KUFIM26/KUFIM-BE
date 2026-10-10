package com.example.kufim.waiting.repository;

import com.example.kufim.waiting.entity.Waiting;
import com.example.kufim.waiting.entity.WaitingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;



public interface WaitingRepository extends JpaRepository<Waiting, Long> {
    Optional<Waiting> findByPublicId(String publicId);
    List<Waiting> findByClientTokenAndStatusIn(String clientToken, Collection<WaitingStatus> statuses);
    List<Waiting> findByBoothIdAndStatusInOrderByRegisteredAtAsc(Long boothId, Collection<WaitingStatus> statuses);
    /** 등록 시각, 같으면 id 순서. 순번 계산과 관리자 명단이 같은 순서를 씁니다. */
    List<Waiting> findByBoothIdAndStatusInOrderByRegisteredAtAscIdAsc(Long boothId, Collection<WaitingStatus> statuses);
    /** 내 앞의 대기 팀 수. 명단 전체를 읽지 않고 (booth_id, status, registered_at) 인덱스로 셉니다. */
    @Query("select count(w) from Waiting w where w.booth.id = :boothId and w.status = com.example.kufim.waiting.entity.WaitingStatus.WAITING"
            + " and (w.registeredAt < :at or (w.registeredAt = :at and w.id < :id))")
    long countAhead(@Param("boothId") Long boothId, @Param("at") LocalDateTime at, @Param("id") Long id);
    /** 부스별 대기 팀 수를 한 번에 셉니다. [boothId, count] */
    @Query("select w.booth.id, count(w) from Waiting w where w.status = com.example.kufim.waiting.entity.WaitingStatus.WAITING group by w.booth.id")
    List<Object[]> countWaitingByBooth();
    long countByBoothIdAndStatus(Long boothId, WaitingStatus status);
    long countByBoothIdAndStatusAndClosedAtGreaterThanEqual(Long boothId, WaitingStatus status, LocalDateTime since);
    long countByBoothIdAndStatusAndRegisteredAtBefore(Long boothId, WaitingStatus status, LocalDateTime before);
    List<Waiting> findByStatusAndAutoCancelAtLessThanEqual(WaitingStatus status, LocalDateTime time);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Waiting w where w.publicId = :publicId")
    Optional<Waiting> lockByPublicId(@Param("publicId") String publicId);
}
