package com.familyapp.infrastructure.allowance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface RecurringAllowancePaymentJpaRepository
        extends JpaRepository<RecurringAllowancePaymentEntity, UUID> {

    boolean existsByMemberIdAndDueDate(UUID memberId, LocalDate dueDate);
}
