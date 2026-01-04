package com.familyapp.infrastructure.dailytask;

import com.familyapp.infrastructure.UuidConverter;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.family.FamilyEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "daily_task")
public class DailyTaskEntity {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean monday;

    @Column(nullable = false)
    private boolean tuesday;

    @Column(nullable = false)
    private boolean wednesday;

    @Column(nullable = false)
    private boolean thursday;

    @Column(nullable = false)
    private boolean friday;

    @Column(nullable = false)
    private boolean saturday;

    @Column(nullable = false)
    private boolean sunday;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired;

    @Column(name = "xp_points", nullable = false)
    private int xpPoints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    @Convert(converter = UuidConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private FamilyEntity family;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany
    @JoinTable(
            name = "daily_task_member",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    private List<FamilyMemberEntity> members = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isMonday() {
        return monday;
    }

    public void setMonday(boolean monday) {
        this.monday = monday;
    }

    public boolean isTuesday() {
        return tuesday;
    }

    public void setTuesday(boolean tuesday) {
        this.tuesday = tuesday;
    }

    public boolean isWednesday() {
        return wednesday;
    }

    public void setWednesday(boolean wednesday) {
        this.wednesday = wednesday;
    }

    public boolean isThursday() {
        return thursday;
    }

    public void setThursday(boolean thursday) {
        this.thursday = thursday;
    }

    public boolean isFriday() {
        return friday;
    }

    public void setFriday(boolean friday) {
        this.friday = friday;
    }

    public boolean isSaturday() {
        return saturday;
    }

    public void setSaturday(boolean saturday) {
        this.saturday = saturday;
    }

    public boolean isSunday() {
        return sunday;
    }

    public void setSunday(boolean sunday) {
        this.sunday = sunday;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public FamilyEntity getFamily() {
        return family;
    }

    public void setFamily(FamilyEntity family) {
        this.family = family;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<FamilyMemberEntity> getMembers() {
        return members;
    }

    public void setMembers(List<FamilyMemberEntity> members) {
        this.members = members;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public void setRequired(boolean required) {
        isRequired = required;
    }

    public int getXpPoints() {
        return xpPoints;
    }

    public void setXpPoints(int xpPoints) {
        this.xpPoints = xpPoints;
    }
}

