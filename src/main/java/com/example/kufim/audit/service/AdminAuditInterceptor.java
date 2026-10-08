package com.example.kufim.audit.service;

import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.audit.entity.AuditLog;
import com.example.kufim.audit.repository.AuditLogRepository;
import com.example.kufim.common.util.ApiClock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {
    private final AuditLogRepository logs;
    private final AdminAccountRepository accounts;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!request.getRequestURI().startsWith("/api/v1/admin/") ||
                !java.util.Set.of("POST", "PATCH", "DELETE", "PUT").contains(request.getMethod()) ||
                request.getRequestURI().endsWith("/auth/login") || response.getStatus() >= 400) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;
        accounts.findByLoginId(auth.getName()).ifPresent(actor -> {
            String uri = request.getRequestURI();
            logs.save(AuditLog.builder().adminAccount(actor).action(request.getMethod() + "_API")
                    .targetType("ADMIN_API").targetId(uri.length() > 64 ? uri.substring(0, 64) : uri)
                    .ip(request.getRemoteAddr()).occurredAt(ApiClock.now()).build());
        });
    }
}
