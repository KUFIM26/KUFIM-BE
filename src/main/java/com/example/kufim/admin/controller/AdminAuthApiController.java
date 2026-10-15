package com.example.kufim.admin.controller;

import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.admin.repository.AdminBoothRepository;
import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.common.util.ApiClock;
import com.example.kufim.security.AdminAccess;
import com.example.kufim.security.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthApiController {
    private final AuthenticationManager manager;
    private final HttpSessionSecurityContextRepository contexts;
    private final AdminAccountRepository accounts;
    private final AdminBoothRepository assignments;
    private final AdminAccess access;
    private final RequestRateLimiter limiter;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Value("${app.rate-limit.admin-login-per-minute:5}") private int loginLimit;

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {}
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    @PostMapping("/login")
    @Transactional
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        limiter.check("admin-login", request.getRemoteAddr(), loginLimit, "A002");
        try {
            var auth = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.loginId(), input.password()));
            var existing = request.getSession(false);
            if (existing != null) request.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            var account = accounts.findByLoginId(auth.getName()).orElseThrow();
            request.getSession(true).setAttribute("adminSessionVersion", account.getSessionVersion());
            account.loggedIn(ApiClock.now());
            var csrf = request.getAttribute("_csrf");
            if (csrf instanceof org.springframework.security.web.csrf.CsrfToken token) token.getToken();
            return ApiResponse.ok(Map.of("account", accountInfo(account), "expiresIn", 1800));
        } catch (AuthenticationException exception) {
            throw new ContractException(HttpStatus.UNAUTHORIZED, "A001", "로그인 정보를 확인해 주세요.");
        }
    }

    /** 본인 비밀번호 변경. 다른 기기의 세션은 끊고 지금 세션은 유지합니다. */
    @PostMapping("/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChange input, HttpServletRequest request) {
        var account = access.current();
        limiter.check("admin-password", String.valueOf(account.getId()), loginLimit, "A002");
        if (!passwords.matches(input.currentPassword(), account.getPasswordHash()))
            throw new ContractException(HttpStatus.BAD_REQUEST, "A001", "현재 비밀번호가 맞지 않습니다.");
        if (input.newPassword().length() < 12)
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "새 비밀번호는 12자 이상이어야 합니다.");
        if (input.newPassword().equals(input.currentPassword()))
            throw new ContractException(HttpStatus.BAD_REQUEST, "C001", "현재와 다른 비밀번호를 입력해 주세요.");
        account.resetPassword(passwords.encode(input.newPassword()));
        request.getSession(true).setAttribute("adminSessionVersion", account.getSessionVersion());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() { return ApiResponse.ok(accountInfo(access.current())); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> accountInfo(com.example.kufim.admin.entity.AdminAccount account) {
        return Map.of("accountId", account.getId(), "name", account.getName(), "role", account.getRole(),
                "managedBoothIds", assignments.findByAdminAccountId(account.getId()).stream().map(a -> a.getBooth().getId()).toList());
    }
}
