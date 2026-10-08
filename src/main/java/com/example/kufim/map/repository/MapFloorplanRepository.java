package com.example.kufim.map.repository;

import com.example.kufim.map.entity.MapFloorplan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;



public interface MapFloorplanRepository extends JpaRepository<MapFloorplan, Long> {
    boolean existsByFestivalId(Long festivalId);
    Optional<MapFloorplan> findTopByFestivalIdOrderByIdDesc(Long festivalId);
}
