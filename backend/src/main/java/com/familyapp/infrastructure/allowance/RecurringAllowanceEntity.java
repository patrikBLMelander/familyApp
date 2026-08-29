package com.familyapp.infrastructure.allowance;

import com.familyapp.infrastructure.UuidConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Ett barns automatiska peng. Ett schema per barn; se V46. */
@Entity
@Table(name = "recurring_allowance")
public class RecurringAllowanceEntity {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID id;

    @Column(name = "member_id", nullable = false, columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID memberId;

    @Column(name = "created_by_member_id", columnDefinition = "VARCHAR(36)", length = 36)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Convert(converter = UuidConverter.class)
    private UUID createdByMemberId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column
    private Integer amount;

    @Column
    private Integer weekday;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "level_1_amount")
    private Integer level1Amount;

    @Column(name = "level_2_amount")
    private Integer level2Amount;

    @Column(name = "level_3_amount")
    private Integer level3Amount;

    @Column(name = "level_4_amount")
    private Integer level4Amount;

    @Column(name = "level_5_amount")
    private Integer level5Amount;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "next_due_on", nullable = false)
    private LocalDate nextDueOn;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Beloppet för en nivå, eller null om nivån saknar belopp. */
    public Integer amountForLevel(int level) {
        return switch (level) {
            case 1 -> level1Amount;
            case 2 -> level2Amount;
            case 3 -> level3Amount;
            case 4 -> level4Amount;
            case 5 -> level5Amount;
            default -> null;
        };
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMemberId() { return memberId; }
    public void setMemberId(UUID memberId) { this.memberId = memberId; }

    public UUID getCreatedByMemberId() { return createdByMemberId; }
    public void setCreatedByMemberId(UUID createdByMemberId) { this.createdByMemberId = createdByMemberId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public Integer getWeekday() { return weekday; }
    public void setWeekday(Integer weekday) { this.weekday = weekday; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public Integer getLevel1Amount() { return level1Amount; }
    public void setLevel1Amount(Integer v) { this.level1Amount = v; }

    public Integer getLevel2Amount() { return level2Amount; }
    public void setLevel2Amount(Integer v) { this.level2Amount = v; }

    public Integer getLevel3Amount() { return level3Amount; }
    public void setLevel3Amount(Integer v) { this.level3Amount = v; }

    public Integer getLevel4Amount() { return level4Amount; }
    public void setLevel4Amount(Integer v) { this.level4Amount = v; }

    public Integer getLevel5Amount() { return level5Amount; }
    public void setLevel5Amount(Integer v) { this.level5Amount = v; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDate getNextDueOn() { return nextDueOn; }
    public void setNextDueOn(LocalDate nextDueOn) { this.nextDueOn = nextDueOn; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
