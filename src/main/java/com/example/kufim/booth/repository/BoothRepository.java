package com.example.kufim.booth.repository;

import com.example.kufim.booth.entity.Booth;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    Page<Booth> findAllByDeletedFalse(Pageable pageable);

    Optional<Booth> findByIdAndDeletedFalse(Long id);
    java.util.List<Booth> findAllByDeletedFalseOrderByIdAsc();
    Optional<Booth> findByBoothCodeAndDeletedFalse(String boothCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booth b where b.id = :id and b.deleted = false")
    Optional<Booth> lockActive(@Param("id") Long id);
}
