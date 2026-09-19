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

/** An affiliate and their portal login. Invite-only: password_hash is null until activated. */
@Entity
@Table(name = "affiliate")
public class AffiliateEntity {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "referral_code", nullable = false, unique = true, length = 64)
    private String referralCode;

    @Column(name = "commission_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPct;

    @Column(name = "commission_month_cap", nullable = false)
    private int commissionMonthCap;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "session_token_hash", length = 64)
    private String sessionTokenHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public BigDecimal getCommissionPct() { return commissionPct; }
    public void setCommissionPct(BigDecimal commissionPct) { this.commissionPct = commissionPct; }

    public int getCommissionMonthCap() { return commissionMonthCap; }
    public void setCommissionMonthCap(int commissionMonthCap) { this.commissionMonthCap = commissionMonthCap; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSessionTokenHash() { return sessionTokenHash; }
    public void setSessionTokenHash(String sessionTokenHash) { this.sessionTokenHash = sessionTokenHash; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
