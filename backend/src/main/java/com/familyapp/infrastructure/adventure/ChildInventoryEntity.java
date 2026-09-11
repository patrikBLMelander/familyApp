package com.familyapp.infrastructure.adventure;

import com.familyapp.infrastructure.UuidConverter;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "child_inventory")
public class ChildInventoryEntity {

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

    /** loot_item id (slug). Kept as a plain column rather than a JPA relation. */
    @Column(name = "item_id", nullable = false, length = 50)
    private String itemId;

    @Column(name = "acquired_at", nullable = false)
    private OffsetDateTime acquiredAt;

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

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public OffsetDateTime getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(OffsetDateTime acquiredAt) {
        this.acquiredAt = acquiredAt;
    }
}
