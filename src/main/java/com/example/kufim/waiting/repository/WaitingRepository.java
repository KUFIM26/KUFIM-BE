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
    long countByBoothIdAndStatus(Long boothId, WaitingStatus status);
    long countByBoothIdAndStatusAndClosedAtGreaterThanEqual(Long boothId, WaitingStatus status, LocalDateTime since);
    long countByBoothIdAndStatusAndRegisteredAtBefore(Long boothId, WaitingStatus status, LocalDateTime before);
    List<Waiting> findByStatusAndAutoCancelAtLessThanEqual(WaitingStatus status, LocalDateTime time);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Waiting w where w.publicId = :publicId")
    Optional<Waiting> lockByPublicId(@Param("publicId") String publicId);
}
