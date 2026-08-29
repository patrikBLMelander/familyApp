package com.familyapp.infrastructure.allowance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringAllowanceJpaRepository extends JpaRepository<RecurringAllowanceEntity, UUID> {

    Optional<RecurringAllowanceEntity> findByMemberId(UUID memberId);

    /** Allt som är förfallet och obetalt -- inte "allt som förfaller idag". */
    List<RecurringAllowanceEntity> findByActiveTrueAndNextDueOnLessThanEqual(LocalDate date);
}
