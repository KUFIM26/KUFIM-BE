package com.example.kufim.translate.controller;

import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.security.AdminAccess;
import com.example.kufim.security.RequestRateLimiter;
import com.example.kufim.translate.service.EnglishBackfillService;
import com.example.kufim.translate.service.TranslationUnavailableException;
import com.example.kufim.translate.service.Translator;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 등록 화면의 자동 번역.
 *
 * <p>번역 업체의 키를 프런트에 둘 수 없어 서버가 대신 호출합니다.
 * 부스 운영자도 자기 부스를 수정하므로 총괄 관리자와 부스 운영자 모두 쓸 수 있습니다.
 */
@RestController
@RequestMapping("/api/v1/admin/translate")
@RequiredArgsConstructor
public class TranslateApiController {
    private static final int MAX_TEXTS = 10;
    private static final int MAX_LENGTH = 1000;

    private final AdminAccess access;
    private final Translator translator;
    private final EnglishBackfillService backfill;
    private final RequestRateLimiter rateLimiter;

    @Value("${app.rate-limit.translate-per-admin-per-minute:30}")
    private int perAdminPerMinute;

    public record TranslateRequest(List<String> texts, String target) {}
    public record TranslateResponse(List<String> translations) {}

    @PostMapping
    @Operation(summary = "한국어 → 영어 번역", description = "관리자 등록 화면에서 영어 칸을 채울 때 씁니다. "
            + "번역 서비스를 쓸 수 없으면 503 T001로 알려 주며, 운영진은 영어를 직접 입력해 등록을 이어 갈 수 있습니다.",
            tags = "관리자 · 번역")
    public ApiResponse<TranslateResponse> translate(@RequestBody TranslateRequest request) {
        var actor = access.current();
        if (request == null || request.texts() == null || request.texts().isEmpty()
                || request.texts().size() > MAX_TEXTS)
            throw invalid("번역할 문구는 1~%d개까지 보낼 수 있습니다.".formatted(MAX_TEXTS));
        if (!"EN".equalsIgnoreCase(request.target() == null ? "EN" : request.target()))
            throw invalid("지금은 영어(EN)로만 번역할 수 있습니다.");
        for (String text : request.texts()) {
            if (text == null || text.isBlank()) throw invalid("빈 문구는 번역할 수 없습니다.");
            if (text.length() > MAX_LENGTH) throw invalid("한 번에 번역할 수 있는 길이는 %d자입니다.".formatted(MAX_LENGTH));
        }
        rateLimiter.check("translate", String.valueOf(actor.getId()), perAdminPerMinute, "C004");
        return ApiResponse.ok(new TranslateResponse(translator.toEnglish(request.texts())));
    }

    @PostMapping("/backfill")
    @Operation(summary = "기존 내용의 영어 칸 일괄 채우기",
            description = "영어가 비어 있는 칸만 번역해 채웁니다. 운영진이 직접 입력해 둔 값은 건드리지 않습니다. "
                    + "한 번만 실행하면 되는 작업이라 총괄 관리자만 호출할 수 있습니다.", tags = "관리자 · 번역")
    public ApiResponse<Map<String, Object>> backfill() {
        access.superAdmin();
        if (!translator.configured()) throw new TranslationUnavailableException();
        return ApiResponse.ok(backfill.run());
    }

    private ContractException invalid(String message) {
        return new ContractException(HttpStatus.BAD_REQUEST, "C001", message);
    }
}
