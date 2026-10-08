package com.example.kufim.admin.repository;

import com.example.kufim.admin.entity.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByLoginId(String loginId);
}
