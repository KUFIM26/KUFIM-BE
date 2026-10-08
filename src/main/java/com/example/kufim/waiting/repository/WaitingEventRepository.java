package com.example.kufim.waiting.repository;

import com.example.kufim.waiting.entity.WaitingEvent;
import org.springframework.data.jpa.repository.JpaRepository;



public interface WaitingEventRepository extends JpaRepository<WaitingEvent, Long> {
}
