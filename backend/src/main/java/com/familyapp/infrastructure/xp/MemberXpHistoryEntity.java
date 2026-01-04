package com.familyapp.infrastructure.xp;

import com.familyapp.infrastructure.UuidConverter;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_xp_history")
public class MemberXpHistoryEntity {

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

    @Column(name = "final_xp", nullable = false)
    private int finalXp;

    @Column(name = "final_level", nullable = false)
    private int finalLevel; // 1-10

    @Column(name = "total_tasks_completed", nullable = false)
    private int totalTasksCompleted;

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

    public int getFinalXp() {
        return finalXp;
    }

    public void setFinalXp(int finalXp) {
        this.finalXp = finalXp;
    }

    public int getFinalLevel() {
        return finalLevel;
    }

    public void setFinalLevel(int finalLevel) {
        this.finalLevel = finalLevel;
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
}

