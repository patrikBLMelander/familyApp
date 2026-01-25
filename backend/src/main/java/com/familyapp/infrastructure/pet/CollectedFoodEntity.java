package com.familyapp.infrastructure.pet;

import com.familyapp.infrastructure.UuidConverter;
import com.familyapp.infrastructure.calendar.CalendarEventEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collected_food")
public class CollectedFoodEntity {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @Convert(converter = UuidConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private FamilyMemberEntity member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = true)
    @Convert(converter = UuidConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CalendarEventEntity event;

    @Column(name = "xp_amount", nullable = false)
    private int xpAmount; // Amount of XP this food item represents (usually 1)

    @Column(name = "is_fed", nullable = false)
    private boolean isFed; // Whether this food has been fed to the pet

    @Column(name = "collected_at", nullable = false)
    private OffsetDateTime collectedAt;

    @Column(name = "fed_at")
    private OffsetDateTime fedAt; // When this food was fed (null if not fed)

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FamilyMemberEntity getMember() {
        return member;
    }

    public void setMember(FamilyMemberEntity member) {
        this.member = member;
    }

    public CalendarEventEntity getEvent() {
        return event;
    }

    public void setEvent(CalendarEventEntity event) {
        this.event = event;
    }

    public int getXpAmount() {
        return xpAmount;
    }

    public void setXpAmount(int xpAmount) {
        this.xpAmount = xpAmount;
    }

    public boolean isFed() {
        return isFed;
    }

    public void setFed(boolean fed) {
        isFed = fed;
    }

    public OffsetDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(OffsetDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }

    public OffsetDateTime getFedAt() {
        return fedAt;
    }

    public void setFedAt(OffsetDateTime fedAt) {
        this.fedAt = fedAt;
    }
}
