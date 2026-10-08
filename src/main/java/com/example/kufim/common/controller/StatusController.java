package com.example.kufim.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "서버 상태")
public class StatusController {
    @GetMapping("/api/v1/status")
    @Operation(summary = "서버 실행 상태 확인")
    public Map<String, String> status() {
        return Map.of("application", "KUFIM-BE", "status", "UP");
    }
}
