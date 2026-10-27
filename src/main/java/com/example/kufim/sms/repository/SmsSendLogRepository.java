package com.example.kufim.sms.repository;

import com.example.kufim.sms.entity.SmsSendLog;
import com.example.kufim.sms.entity.SmsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;



public interface SmsSendLogRepository extends JpaRepository<SmsSendLog, Long> {

    /** 일일 발송 한도를 셀 때 씁니다. 실제로 나간 건만 비용이 들므로 SENT만 셉니다. */
    long countByStatusAndSentAtGreaterThanEqual(SmsStatus status, LocalDateTime from);
}
