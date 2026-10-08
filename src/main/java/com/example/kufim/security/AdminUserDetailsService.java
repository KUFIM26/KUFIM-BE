package com.example.kufim.security;

import com.example.kufim.admin.repository.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {
    private final AdminAccountRepository repository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var account = repository.findByLoginId(username)
                .orElseThrow(() -> new UsernameNotFoundException("인증 정보를 확인해 주세요."));
        return User.withUsername(account.getLoginId()).password(account.getPasswordHash())
                .roles(account.getRole().name()).disabled(!account.isActive()).build();
    }
}
