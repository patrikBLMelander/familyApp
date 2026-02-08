package com.familyapp.infrastructure.wallet;

import com.familyapp.infrastructure.UuidConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "wallet_transaction_savings_goal")
public class WalletTransactionSavingsGoalEntity {

    @EmbeddedId
    private WalletTransactionSavingsGoalId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, insertable = false, updatable = false)
    @Convert(converter = UuidConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private WalletTransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "savings_goal_id", nullable = false, insertable = false, updatable = false)
    @Convert(converter = UuidConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SavingsGoalEntity savingsGoal;

    @Column(nullable = false)
    private int amount;

    public WalletTransactionSavingsGoalId getId() {
        return id;
    }

    public void setId(WalletTransactionSavingsGoalId id) {
        this.id = id;
    }

    public WalletTransactionEntity getTransaction() {
        return transaction;
    }

    public void setTransaction(WalletTransactionEntity transaction) {
        this.transaction = transaction;
    }

    public SavingsGoalEntity getSavingsGoal() {
        return savingsGoal;
    }

    public void setSavingsGoal(SavingsGoalEntity savingsGoal) {
        this.savingsGoal = savingsGoal;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}
