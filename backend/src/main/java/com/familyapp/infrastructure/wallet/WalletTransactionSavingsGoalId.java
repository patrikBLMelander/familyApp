package com.familyapp.infrastructure.wallet;

import com.familyapp.infrastructure.UuidConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WalletTransactionSavingsGoalId implements java.io.Serializable {
    @Column(name = "transaction_id", columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID transaction;

    @Column(name = "savings_goal_id", columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID savingsGoal;

    public WalletTransactionSavingsGoalId() {
    }

    public WalletTransactionSavingsGoalId(UUID transaction, UUID savingsGoal) {
        this.transaction = transaction;
        this.savingsGoal = savingsGoal;
    }

    public UUID getTransaction() {
        return transaction;
    }

    public void setTransaction(UUID transaction) {
        this.transaction = transaction;
    }

    public UUID getSavingsGoal() {
        return savingsGoal;
    }

    public void setSavingsGoal(UUID savingsGoal) {
        this.savingsGoal = savingsGoal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WalletTransactionSavingsGoalId that = (WalletTransactionSavingsGoalId) o;
        return Objects.equals(transaction, that.transaction) &&
                Objects.equals(savingsGoal, that.savingsGoal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transaction, savingsGoal);
    }
}
