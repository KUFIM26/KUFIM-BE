package com.example.kufim.sms.service;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.sms.entity.SmsMessageType;
import com.example.kufim.sms.entity.SmsSendLog;
import com.example.kufim.sms.entity.SmsStatus;
import com.example.kufim.sms.repository.SmsSendLogRepository;
import com.example.kufim.waiting.entity.Waiting;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.time.LocalDateTime;

/**
 * 입장 호출 문자를 보내고 이력을 남깁니다.
 * 비용이 실제로 나가므로 일일 한도를 넘으면 보내지 않고 SKIPPED_LIMIT으로 적으며,
 * 그 경우에도 웨이팅 호출 자체는 그대로 진행됩니다(문자는 부가 수단입니다).
 */
@Service
@RequiredArgsConstructor
public class WaitingSmsSender {
    /** 개인정보 파기 후 전화번호 자리에 남는 값. 이 번호로는 보내지 않습니다. */
    private static final String PURGED_NUMBER = "00000000000";
    /** 솔라피 기준 SMS(단문) 한계. 넘으면 LMS로 전환되어 요금이 올라갑니다. */
    private static final int SMS_BYTE_LIMIT = 90;
    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    private final SolapiClient solapi;
    private final SmsSendLogRepository logs;

    @Value("${app.sms.daily-limit:500}")
    private int dailyLimit;

    /**
     * 호출/재호출 시점에 한 통 보냅니다. 예외를 밖으로 내보내지 않습니다.
     *
     * @return 이력에 적힌 상태. 응답의 smsStatus로 그대로 내려보냅니다.
     */
    public SmsStatus sendCall(Waiting waiting, SmsMessageType type, AdminAccount actor) {
        SolapiResult result = resolve(waiting);
        logs.save(SmsSendLog.builder()
                .waiting(waiting)
                .messageType(type)
                .status(result.status())
                .providerId(result.providerId())
                .errorCode(result.errorCode())
                .cost(result.cost())
                .requestedBy(actor)
                .sentAt(ApiClock.now())
                .build());
        return result.status();
    }

    private SolapiResult resolve(Waiting waiting) {
        String phone = waiting.getPhoneNumber();
        if (phone == null || phone.isBlank() || PURGED_NUMBER.equals(phone))
            return SolapiResult.skippedNoNumber();
        if (!solapi.configured())
            return SolapiResult.notConfigured();
        if (reachedDailyLimit())
            return SolapiResult.skippedLimit();
        return solapi.send(phone, message(waiting));
    }

    private boolean reachedDailyLimit() {
        if (dailyLimit <= 0) return false;
        LocalDateTime startOfToday = ApiClock.now().toLocalDate().atStartOfDay();
        return logs.countByStatusAndSentAtGreaterThanEqual(SmsStatus.SENT, startOfToday) >= dailyLimit;
    }

    /** 단문 한도를 넘지 않도록 부스 이름을 필요한 만큼만 줄여 붙입니다. */
    private String message(Waiting waiting) {
        String booth = eucSafe(waiting.getBooth().getName());
        String text = build(booth);
        while (text.getBytes(EUC_KR).length > SMS_BYTE_LIMIT && booth.length() > 4) {
            booth = booth.substring(0, booth.length() - 1);
            text = build(booth.stripTrailing() + "…");
        }
        return text;
    }

    private String build(String booth) {
        return "[KUFIM] %s 입장 순서입니다. 부스로 와 주세요.".formatted(booth);
    }

    /**
     * 문자는 EUC-KR로 변환되어 나가므로, 그 표에 없는 글자는 받는 쪽에서 '?'로 깨집니다.
     * 줄표류는 모양이 같은 U+2015로 바꾸고, 그래도 변환할 수 없는 글자는 공백으로 둡니다.
     */
    private String eucSafe(String value) {
        String normalized = value.replace('—', '―').replace('–', '―');
        var encoder = EUC_KR.newEncoder();
        StringBuilder safe = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(code -> {
            String ch = new String(Character.toChars(code));
            safe.append(encoder.canEncode(ch) ? ch : " ");
        });
        return safe.toString().replaceAll(" {2,}", " ").trim();
    }
}
