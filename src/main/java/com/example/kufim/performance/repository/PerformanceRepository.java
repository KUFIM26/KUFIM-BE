package com.example.kufim.performance.repository;

import com.example.kufim.performance.entity.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    Page<Performance> findAllByDeletedFalse(Pageable pageable);

    Optional<Performance> findByIdAndDeletedFalse(Long id);
    java.util.List<Performance> findAllByDeletedFalseOrderByStartAtAsc();
}
