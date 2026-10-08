package com.example.kufim.admin.repository;

import com.example.kufim.admin.entity.AdminBooth;
import org.springframework.data.jpa.repository.JpaRepository;



public interface AdminBoothRepository extends JpaRepository<AdminBooth, Long> {
    boolean existsByAdminAccountIdAndBoothId(Long adminAccountId, Long boothId);
    java.util.List<AdminBooth> findByAdminAccountId(Long adminAccountId);
    void deleteByAdminAccountId(Long adminAccountId);
}
