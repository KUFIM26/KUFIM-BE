package com.example.kufim.facility.repository;

import com.example.kufim.facility.entity.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    Page<Facility> findAllByDeletedFalse(Pageable pageable);

    Optional<Facility> findByIdAndDeletedFalse(Long id);
    java.util.List<Facility> findAllByDeletedFalseOrderByIdAsc();
}
