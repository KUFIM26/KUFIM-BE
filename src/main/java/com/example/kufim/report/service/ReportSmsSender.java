package com.example.kufim.report.service;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.report.entity.Report;
import com.example.kufim.report.entity.ReportStatus;
import com.example.kufim.sms.entity.SmsMessageType;
import com.example.kufim.sms.entity.SmsSendLog;
import com.example.kufim.sms.entity.SmsStatus;
import com.example.kufim.sms.repository.SmsSendLogRepository;
import com.example.kufim.sms.service.SmsText;
import com.example.kufim.sms.service.SolapiClient;
import com.example.kufim.sms.service.SolapiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 신고 상태 변경 문자를 보내고 이력을 남깁니다.
 * 발송이 실패해도 예외를 밖으로 내보내지 않아 상태 변경 자체는 그대로 진행됩니다.
 */
@Service
@RequiredArgsConstructor
public class ReportSmsSender {
    /** 개인정보 파기 후 전화번호 자리에 남는 값. 이 번호로는 보내지 않습니다. */
    private static final String PURGED_NUMBER = "00000000000";

    private final SolapiClient solapi;
    private final SmsSendLogRepository logs;

    @Value("${app.sms.daily-limit:500}")
    private int dailyLimit;

    /**
     * 상태가 실제로 바뀐 경우에만 호출합니다.
     *
     * @return FE 계약상의 발송 상태 문자열(SENT/FAILED/NOT_CONFIGURED/LIMIT_EXCEEDED/SKIPPED).
     */
    public String send(Report report, ReportStatus status, AdminAccount actor) {
        SolapiResult result = resolve(report, status);
        logs.save(SmsSendLog.builder()
                .report(report)
                .messageType(SmsMessageType.REPORT_STATUS)
                .status(result.status())
                .providerId(result.providerId())
                .errorCode(result.errorCode())
                .cost(result.cost())
                .requestedBy(actor)
                .sentAt(ApiClock.now())
                .build());
        return contractStatus(result);
    }

    private SolapiResult resolve(Report report, ReportStatus status) {
        String phone = report.getPhoneNumber();
        if (phone == null || phone.isBlank() || PURGED_NUMBER.equals(phone))
            return SolapiResult.skippedNoNumber();
        if (!solapi.configured())
            return SolapiResult.notConfigured();
        if (reachedDailyLimit())
            return SolapiResult.skippedLimit();
        return solapi.send(phone, message(report, status));
    }

    private boolean reachedDailyLimit() {
        if (dailyLimit <= 0) return false;
        LocalDateTime startOfToday = ApiClock.now().toLocalDate().atStartOfDay();
        return logs.countByStatusAndSentAtGreaterThanEqual(SmsStatus.SENT, startOfToday) >= dailyLimit;
    }

    /** 제목과 상태를 알리고, 처리 메모가 있으면 단문 한도 안에서 덧붙입니다. */
    private String message(Report report, ReportStatus status) {
        String head = "[KUFIM] '%s' 신고가 %s 상태로 변경되었습니다."
                .formatted(SmsText.safe(report.getTitle()), status.label());
        String note = SmsText.safe(report.getAdminNote());
        if (note.isBlank()) return SmsText.fit(head);
        String full = head + " " + note;
        return SmsText.bytes(full) <= SmsText.SMS_BYTE_LIMIT ? full : SmsText.fit(head);
    }

    /**
     * 이력에 적는 값과 FE에 내려보내는 값이 다릅니다.
     * 한도 초과는 LIMIT_EXCEEDED, 번호가 없어 보내지 않은 경우는 SKIPPED로 약속되어 있습니다.
     */
    private String contractStatus(SolapiResult result) {
        if (result.status() == SmsStatus.SKIPPED_LIMIT)
            return "DAILY_LIMIT_REACHED".equals(result.errorCode()) ? "LIMIT_EXCEEDED" : "SKIPPED";
        return result.status().name();
    }
}
