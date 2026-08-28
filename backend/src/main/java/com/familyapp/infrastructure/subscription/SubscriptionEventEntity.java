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

/**
 * One provider webhook, stored verbatim.
 *
 * Two jobs. The unique constraint on providerEventId is what makes webhook handling
 * idempotent -- a redelivery is recognised rather than applied twice. And the raw
 * payload is the only record of what the store actually said, which is what a billing
 * dispute is reconstructed from months later.
 *
 * familyId is nullable on purpose: an event whose app_user_id resolves to no family
 * is still worth keeping. Discarding it would lose the one piece of evidence
 * explaining why a paying family never became entitled.
 */
@Entity
@Table(name = "subscription_event")
public class SubscriptionEventEntity {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID id;

    @Column(name = "family_id", columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID familyId;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public void setFamilyId(UUID familyId) {
        this.familyId = familyId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
