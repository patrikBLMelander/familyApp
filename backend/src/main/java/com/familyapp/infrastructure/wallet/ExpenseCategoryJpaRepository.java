package com.familyapp.infrastructure.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseCategoryJpaRepository extends JpaRepository<ExpenseCategoryEntity, UUID> {
    List<ExpenseCategoryEntity> findAllByOrderByNameAsc();
}
