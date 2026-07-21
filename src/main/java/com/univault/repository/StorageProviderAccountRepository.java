package com.univault.repository;

import com.univault.entity.StorageProviderAccount;
import com.univault.entity.StorageProviderAccount.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StorageProviderAccountRepository extends JpaRepository<StorageProviderAccount, Long> {
    List<StorageProviderAccount> findByUserId(Long userId);
    List<StorageProviderAccount> findByUserIdAndStatus(Long userId, AccountStatus status);
}