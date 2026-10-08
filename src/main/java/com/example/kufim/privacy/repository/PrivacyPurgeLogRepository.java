package com.example.kufim.privacy.repository;

import com.example.kufim.privacy.entity.PrivacyPurgeLog;
import org.springframework.data.jpa.repository.JpaRepository;



public interface PrivacyPurgeLogRepository extends JpaRepository<PrivacyPurgeLog, Long> {
}
