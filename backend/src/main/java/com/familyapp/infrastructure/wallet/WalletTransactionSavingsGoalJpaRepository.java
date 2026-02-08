package com.familyapp.infrastructure.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletTransactionSavingsGoalJpaRepository extends JpaRepository<WalletTransactionSavingsGoalEntity, WalletTransactionSavingsGoalId> {
    @Query("SELECT w FROM WalletTransactionSavingsGoalEntity w WHERE w.id.transaction = :transactionId")
    List<WalletTransactionSavingsGoalEntity> findByTransactionId(@Param("transactionId") UUID transactionId);
}
