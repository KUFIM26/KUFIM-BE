package com.example.kufim.festival.repository;

import com.example.kufim.festival.entity.Festival;
import org.springframework.data.jpa.repository.JpaRepository;



public interface FestivalRepository extends JpaRepository<Festival, Long> {
}
