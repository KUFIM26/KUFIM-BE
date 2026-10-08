package com.example.kufim.admin.controller;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminBooth;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.admin.repository.AdminBoothRepository;
import com.example.kufim.booth.repository.BoothRepository;
import com.example.kufim.common.dto.ApiResponse;
import com.example.kufim.common.exception.ContractException;
import com.example.kufim.security.AdminAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountApiController {
    private final AdminAccess access;
    private final AdminAccountRepository accounts;
    private final AdminBoothRepository assignments;
    private final BoothRepository booths;
    private final PasswordEncoder passwords;

    public record Create(@NotBlank String loginId, @NotBlank String name, @NotNull AdminRole role, List<Long> managedBoothIds) {}
    public record Change(String name, AdminRole role, Boolean active, List<Long> managedBoothIds) {}
    public record PasswordReset(@NotBlank String newPassword) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> list() {
        access.superAdmin(); return ApiResponse.ok(accounts.findAll().stream().map(this::view).toList());
    }
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> one(@PathVariable long id) {
        access.superAdmin(); return ApiResponse.ok(view(accounts.findById(id).orElseThrow(this::missing)));
    }
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody @Valid Create input) {
        access.superAdmin();
        if (accounts.findByLoginId(input.loginId()).isPresent()) throw invalid("이미 사용 중인 로그인 ID입니다.");
        var account = accounts.save(AdminAccount.builder().loginId(input.loginId()).name(input.name()).role(input.role())
                .passwordHash(passwords.encode(UUID.randomUUID().toString())).active(false).build());
        assign(account, input.managedBoothIds());
        return ResponseEntity.status(201).body(ApiResponse.ok(view(account)));
    }
    @PatchMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> change(@PathVariable long id, @RequestBody Change input) {
        access.superAdmin(); var account = accounts.findById(id).orElseThrow(this::missing);
        account.change(input.name(), input.role(), input.active());
        if (input.role() != null || input.active() != null) account.forceLogout();
        if (input.managedBoothIds() != null) {
            assignments.deleteByAdminAccountId(id);
            assignments.flush();
            assign(account, input.managedBoothIds());
        }
        return ApiResponse.ok(view(account));
    }
    @PostMapping("/{id}/reset-password")
    @Transactional
    public ResponseEntity<Void> reset(@PathVariable long id, @RequestBody @Valid PasswordReset input) {
        access.superAdmin();
        if (input.newPassword().length() < 12) throw invalid("비밀번호는 12자 이상이어야 합니다.");
        accounts.findById(id).orElseThrow(this::missing).resetPassword(passwords.encode(input.newPassword()));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/force-logout")
    @Transactional
    public ResponseEntity<Void> forceLogout(@PathVariable long id) {
        access.superAdmin(); accounts.findById(id).orElseThrow(this::missing).forceLogout();
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        access.superAdmin(); accounts.findById(id).orElseThrow(this::missing).change(null, null, false);
        return ResponseEntity.noContent().build();
    }

    private void assign(AdminAccount account, List<Long> boothIds) {
        if (account.getRole() == AdminRole.SUPER_ADMIN) return;
        for (var boothId : boothIds == null ? List.<Long>of() : new LinkedHashSet<>(boothIds)) {
            var booth = booths.findByIdAndDeletedFalse(boothId).orElseThrow(() -> invalid("부스를 찾을 수 없습니다."));
            assignments.save(AdminBooth.builder().adminAccount(account).booth(booth).build());
        }
    }
    private Map<String, Object> view(AdminAccount account) {
        return Map.of("accountId", account.getId(), "loginId", account.getLoginId(), "name", account.getName(),
                "role", account.getRole(), "active", account.isActive(), "managedBoothIds",
                assignments.findByAdminAccountId(account.getId()).stream().map(a -> a.getBooth().getId()).toList());
    }
    private ContractException missing() { return new ContractException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","계정을 찾을 수 없습니다."); }
    private ContractException invalid(String message) { return new ContractException(HttpStatus.BAD_REQUEST,"C001",message); }
}
