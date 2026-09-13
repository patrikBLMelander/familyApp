package com.familyapp.infrastructure.adventure;

import com.familyapp.infrastructure.UuidConverter;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "adventure")
public class AdventureEntity {

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

    @Column(nullable = false, length = 50)
    private String scene;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "duration_secs", nullable = false)
    private int durationSecs;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "loot_type", length = 20)
    private String lootType;

    @Column(name = "loot_ref", length = 50)
    private String lootRef;

    @Column(name = "loot_qty")
    private Integer lootQty;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public int getDurationSecs() {
        return durationSecs;
    }

    public void setDurationSecs(int durationSecs) {
        this.durationSecs = durationSecs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLootType() {
        return lootType;
    }

    public void setLootType(String lootType) {
        this.lootType = lootType;
    }

    public String getLootRef() {
        return lootRef;
    }

    public void setLootRef(String lootRef) {
        this.lootRef = lootRef;
    }

    public Integer getLootQty() {
        return lootQty;
    }

    public void setLootQty(Integer lootQty) {
        this.lootQty = lootQty;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(OffsetDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
