package com.familyapp.infrastructure.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransactionEntity, UUID> {
    List<WalletTransactionEntity> findByWalletIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID walletId);
    List<WalletTransactionEntity> findByWalletIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID walletId, org.springframework.data.domain.Pageable pageable);
}
