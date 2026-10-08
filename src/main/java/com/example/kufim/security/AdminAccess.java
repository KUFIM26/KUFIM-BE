package com.example.kufim.security;

import com.example.kufim.admin.entity.AdminAccount;
import com.example.kufim.admin.entity.AdminRole;
import com.example.kufim.admin.repository.AdminAccountRepository;
import com.example.kufim.admin.repository.AdminBoothRepository;
import com.example.kufim.common.exception.ContractException;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAccess {
    private final AdminAccountRepository accounts;
    private final AdminBoothRepository assignments;
    private final HttpServletRequest request;
    public AdminAccount current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal()))
            throw new ContractException(HttpStatus.UNAUTHORIZED, "A004", "관리자 세션이 필요합니다.");
        var account = accounts.findByLoginId(auth.getName()).filter(AdminAccount::isActive)
                .orElseThrow(() -> new ContractException(HttpStatus.UNAUTHORIZED, "A004", "관리자 세션이 만료되었습니다."));
        var session = request.getSession(false);
        if (session == null || !Integer.valueOf(account.getSessionVersion()).equals(session.getAttribute("adminSessionVersion")))
            throw new ContractException(HttpStatus.UNAUTHORIZED, "A004", "관리자 세션이 만료되었습니다.");
        return account;
    }
    public AdminAccount booth(long boothId) {
        var account = current();
        if (account.getRole() != AdminRole.SUPER_ADMIN && !assignments.existsByAdminAccountIdAndBoothId(account.getId(), boothId))
            throw new ContractException(HttpStatus.FORBIDDEN, "A003", "담당 부스에 대한 권한이 없습니다.");
        return account;
    }
    public AdminAccount superAdmin() {
        var account = current();
        if (account.getRole() != AdminRole.SUPER_ADMIN)
            throw new ContractException(HttpStatus.FORBIDDEN, "A003", "총괄 관리자 권한이 필요합니다.");
        return account;
    }
}
