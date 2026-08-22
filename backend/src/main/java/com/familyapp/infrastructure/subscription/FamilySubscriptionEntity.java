package com.familyapp.infrastructure.subscription;

import com.familyapp.infrastructure.UuidConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "family_subscription")
public class FamilySubscriptionEntity {

    @Id
    @Column(name = "family_id", columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID familyId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "trial_started_at", nullable = false)
    private OffsetDateTime trialStartedAt;

    @Column(name = "trial_ends_at", nullable = false)
    private OffsetDateTime trialEndsAt;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Column(length = 16)
    private String platform;

    @Column(name = "store_product_id", length = 100)
    private String storeProductId;

    @Column(name = "store_transaction_id")
    private String storeTransactionId;

    @Column(name = "provider_customer_id")
    private String providerCustomerId;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "is_comped", nullable = false)
    private boolean comped;

    @Column(name = "comp_expires_at")
    private OffsetDateTime compExpiresAt;

    @Column(name = "comp_reason")
    private String compReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getTrialStartedAt() { return trialStartedAt; }
    public void setTrialStartedAt(OffsetDateTime trialStartedAt) { this.trialStartedAt = trialStartedAt; }

    public OffsetDateTime getTrialEndsAt() { return trialEndsAt; }
    public void setTrialEndsAt(OffsetDateTime trialEndsAt) { this.trialEndsAt = trialEndsAt; }

    public OffsetDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(OffsetDateTime currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStoreProductId() { return storeProductId; }
    public void setStoreProductId(String storeProductId) { this.storeProductId = storeProductId; }

    public String getStoreTransactionId() { return storeTransactionId; }
    public void setStoreTransactionId(String storeTransactionId) { this.storeTransactionId = storeTransactionId; }

    public String getProviderCustomerId() { return providerCustomerId; }
    public void setProviderCustomerId(String providerCustomerId) { this.providerCustomerId = providerCustomerId; }

    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) { this.cancelAtPeriodEnd = cancelAtPeriodEnd; }

    public boolean isComped() { return comped; }
    public void setComped(boolean comped) { this.comped = comped; }

    public OffsetDateTime getCompExpiresAt() { return compExpiresAt; }
    public void setCompExpiresAt(OffsetDateTime compExpiresAt) { this.compExpiresAt = compExpiresAt; }

    public String getCompReason() { return compReason; }
    public void setCompReason(String compReason) { this.compReason = compReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
