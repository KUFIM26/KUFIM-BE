package com.example.kufim.sms.service;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 솔라피(Solapi) 문자 발송 연동.
 * 인증은 "HMAC-SHA256 apiKey=…, date=…, salt=…, signature=…" 헤더를 쓰며,
 * signature는 (date + salt)를 API Secret 키로 HMAC-SHA256 해 16진수로 적은 값입니다.
 * date는 초 단위 ISO 8601(UTC)이어야 하고 서버 시각과 ±15분을 넘으면 거부됩니다.
 * API 키가 비어 있으면 {@link #configured()}가 false가 되어 아무것도 호출하지 않습니다.
 */
@Slf4j
@Component
public class SolapiClient {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String apiKey;
    private final String apiSecret;
    private final String sender;
    private final RestClient client;

    public SolapiClient(@Value("${app.sms.api-key:}") String apiKey,
            @Value("${app.sms.api-secret:}") String apiSecret,
            @Value("${app.sms.sender:}") String sender,
            @Value("${app.sms.base-url:https://api.solapi.com}") String baseUrl,
            @Value("${app.sms.timeout-seconds:3}") int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.sender = sender == null ? "" : sender.replaceAll("[^0-9]", "");
        // 호출 API는 운영진이 버튼을 누르고 기다리는 경로라 짧게 끊습니다.
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        // RestClient.Builder 자동 구성은 web starter에만 있어 직접 만듭니다.
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** API 키·시크릿·발신번호가 모두 있을 때만 발송을 시도합니다. */
    public boolean configured() {
        return !apiKey.isBlank() && !apiSecret.isBlank() && !sender.isBlank();
    }

    private String authorization() {
        String date = ZonedDateTime.now(ZoneOffset.UTC).format(DATE);
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of()
                    .formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
            return "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s".formatted(apiKey, date, salt, signature);
        } catch (Exception e) {
            throw new IllegalStateException("문자 발송 서명을 만들지 못했습니다.", e);
        }
    }

    /**
     * 한 사람에게 한 통 보냅니다. 예외를 던지지 않고 결과를 그대로 돌려주므로,
     * 호출 측은 실패해도 입장 호출 자체는 계속 진행할 수 있습니다.
     */
    public SolapiResult send(String to, String text) {
        if (!configured()) return SolapiResult.notConfigured();
        String normalized = to == null ? "" : to.replaceAll("[^0-9]", "");
        if (normalized.length() < 10) return SolapiResult.failed("INVALID_NUMBER", null);
        try {
            JsonNode body = client.post()
                    .uri("/messages/v4/send-many/detail")
                    .header("Authorization", authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messages", List.of(Map.of("to", normalized, "from", sender, "text", text)),
                            "showMessageList", true,
                            "allowDuplicates", true))
                    .retrieve()
                    .body(JsonNode.class);
            return parse(body);
        } catch (Exception e) {
            log.warn("문자 발송 실패: {}", e.toString());
            return SolapiResult.failed("PROVIDER_ERROR", null);
        }
    }

    /** 접수는 됐지만 건별로 실패할 수 있어 failedMessageList를 먼저 봅니다. */
    private SolapiResult parse(JsonNode body) {
        if (body == null) return SolapiResult.failed("EMPTY_RESPONSE", null);
        JsonNode failed = body.path("failedMessageList");
        if (failed.isArray() && !failed.isEmpty()) {
            JsonNode first = failed.get(0);
            return SolapiResult.failed(first.path("statusCode").asString("UNKNOWN"),
                    first.path("messageId").asString(null));
        }
        JsonNode list = body.path("messageList");
        String messageId = list.isArray() && !list.isEmpty()
                ? list.get(0).path("messageId").asString(null) : null;
        Integer cost = body.path("groupInfo").path("countForCharge").isNumber()
                ? body.path("groupInfo").path("countForCharge").asInt() : null;
        return SolapiResult.sent(messageId, cost);
    }
}
