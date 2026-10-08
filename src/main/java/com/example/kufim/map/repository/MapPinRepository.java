package com.example.kufim.map.repository;

import com.example.kufim.map.entity.MapPin;
import org.springframework.data.jpa.repository.JpaRepository;



public interface MapPinRepository extends JpaRepository<MapPin, Long> {
    java.util.Optional<MapPin> findByTargetTypeAndTargetId(com.example.kufim.map.entity.MapTargetType type, Long targetId);
}
