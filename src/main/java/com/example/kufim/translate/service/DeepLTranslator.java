package com.example.kufim.translate.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * DeepL 번역 연동.
 *
 * <p>인증은 {@code Authorization: DeepL-Auth-Key <키>} 헤더입니다. 무료 플랜 키는 {@code :fx}로
 * 끝나고 {@code api-free.deepl.com}을, 유료 키는 {@code api.deepl.com}을 씁니다. 키 모양으로
 * 자동 판별하되 {@code app.translate.deepl.base-url}로 덮어쓸 수 있습니다.
 *
 * <p>키가 비어 있으면 {@link #configured()}가 false가 되어 아무것도 호출하지 않습니다.
 * 자동 번역이 없어도 운영진이 영어를 직접 입력할 수 있으므로 등록 기능 자체는 그대로 돕니다.
 */
@Slf4j
@Component
public class DeepLTranslator implements Translator {
    private static final String FREE_BASE_URL = "https://api-free.deepl.com";
    private static final String PRO_BASE_URL = "https://api.deepl.com";

    private final String apiKey;
    private final RestClient client;

    public DeepLTranslator(@Value("${app.translate.deepl.api-key:}") String apiKey,
            @Value("${app.translate.deepl.base-url:}") String baseUrl,
            @Value("${app.translate.deepl.timeout-seconds:5}") int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String resolved = baseUrl == null || baseUrl.isBlank()
                ? (this.apiKey.endsWith(":fx") ? FREE_BASE_URL : PRO_BASE_URL)
                : baseUrl.trim();
        // 운영진이 입력칸을 벗어날 때마다 기다리는 경로라 짧게 끊습니다.
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(resolved).requestFactory(factory).build();
    }

    @Override
    public boolean configured() {
        return !apiKey.isBlank();
    }

    @Override
    public List<String> toEnglish(List<String> texts) {
        if (!configured()) throw new TranslationUnavailableException();
        JsonNode body;
        try {
            body = client.post().uri("/v2/translate")
                    .header("Authorization", "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", texts, "source_lang", "KO", "target_lang", "EN-US"))
                    .retrieve().body(JsonNode.class);
        } catch (Exception e) {
            // 한도 초과(456)·인증 실패(403)·장애를 가리지 않고 같은 응답을 줍니다.
            // 운영진 화면에서 할 수 있는 일은 어느 쪽이든 "직접 입력"뿐입니다.
            log.warn("DeepL 번역 호출 실패: {}", e.toString());
            throw new TranslationUnavailableException();
        }
        var translations = body == null ? null : body.get("translations");
        if (translations == null || !translations.isArray() || translations.size() != texts.size()) {
            log.warn("DeepL 응답을 해석할 수 없습니다. 보낸 개수={}", texts.size());
            throw new TranslationUnavailableException();
        }
        List<String> result = new ArrayList<>(texts.size());
        for (JsonNode node : translations) {
            var text = node.get("text");
            if (text == null) throw new TranslationUnavailableException();
            result.add(text.asString());
        }
        return result;
    }
}
