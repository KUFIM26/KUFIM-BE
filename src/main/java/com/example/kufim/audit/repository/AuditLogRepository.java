package com.example.kufim.audit.repository;

import com.example.kufim.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;



public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
