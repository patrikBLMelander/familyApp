package com.familyapp.infrastructure.affiliate;

import com.familyapp.infrastructure.UuidConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One paid period's commission. UNIQUE(subscription_event_ref) makes accrual idempotent. */
@Entity
@Table(name = "affiliate_commission")
public class AffiliateCommissionEntity {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID id;

    @Column(name = "affiliate_id", nullable = false, columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID affiliateId;

    @Column(name = "family_id", nullable = false, columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID familyId;

    @Column(name = "subscription_event_ref", nullable = false, unique = true)
    private String subscriptionEventRef;

    @Column(name = "period_index", nullable = false)
    private int periodIndex;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "payout_id", columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID payoutId;

    @Column(name = "earned_at", nullable = false)
    private OffsetDateTime earnedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAffiliateId() { return affiliateId; }
    public void setAffiliateId(UUID affiliateId) { this.affiliateId = affiliateId; }

    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }

    public String getSubscriptionEventRef() { return subscriptionEventRef; }
    public void setSubscriptionEventRef(String subscriptionEventRef) { this.subscriptionEventRef = subscriptionEventRef; }

    public int getPeriodIndex() { return periodIndex; }
    public void setPeriodIndex(int periodIndex) { this.periodIndex = periodIndex; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getPayoutId() { return payoutId; }
    public void setPayoutId(UUID payoutId) { this.payoutId = payoutId; }

    public OffsetDateTime getEarnedAt() { return earnedAt; }
    public void setEarnedAt(OffsetDateTime earnedAt) { this.earnedAt = earnedAt; }
}
