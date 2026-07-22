package com.univault.repository;

import com.univault.entity.StorageProviderAccount;
import com.univault.entity.StorageProviderAccount.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StorageProviderAccountRepository extends JpaRepository<StorageProviderAccount, Long> {
    List<StorageProviderAccount> findByUserId(UUID userId);
    List<StorageProviderAccount> findByUserIdAndStatus(Long userId, AccountStatus status);
}