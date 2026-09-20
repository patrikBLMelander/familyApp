package com.familyapp.infrastructure.affiliate;

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

/** Which family belongs to which affiliate. UNIQUE(family_id) enforces first-touch. */
@Entity
@Table(name = "affiliate_referral")
public class AffiliateReferralEntity {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID id;

    @Column(name = "affiliate_id", nullable = false, columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID affiliateId;

    @Column(name = "family_id", nullable = false, unique = true, columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID familyId;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "attributed_at", nullable = false)
    private OffsetDateTime attributedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAffiliateId() { return affiliateId; }
    public void setAffiliateId(UUID affiliateId) { this.affiliateId = affiliateId; }

    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public OffsetDateTime getAttributedAt() { return attributedAt; }
    public void setAttributedAt(OffsetDateTime attributedAt) { this.attributedAt = attributedAt; }
}
