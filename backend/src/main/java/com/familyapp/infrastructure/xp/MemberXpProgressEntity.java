package com.familyapp.infrastructure.xp;

import com.familyapp.infrastructure.UuidConverter;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_xp_progress")
public class MemberXpProgressEntity {

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

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month; // 1-12

    @Column(name = "current_xp", nullable = false)
    private int currentXp;

    @Column(name = "current_level", nullable = false)
    private int currentLevel; // 1-10

    @Column(name = "total_tasks_completed", nullable = false)
    private int totalTasksCompleted;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getCurrentXp() {
        return currentXp;
    }

    public void setCurrentXp(int currentXp) {
        this.currentXp = currentXp;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    public int getTotalTasksCompleted() {
        return totalTasksCompleted;
    }

    public void setTotalTasksCompleted(int totalTasksCompleted) {
        this.totalTasksCompleted = totalTasksCompleted;
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
}

