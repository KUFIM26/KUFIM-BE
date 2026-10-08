package com.example.kufim.sms.repository;

import com.example.kufim.sms.entity.SmsSendLog;
import org.springframework.data.jpa.repository.JpaRepository;



public interface SmsSendLogRepository extends JpaRepository<SmsSendLog, Long> {
}
