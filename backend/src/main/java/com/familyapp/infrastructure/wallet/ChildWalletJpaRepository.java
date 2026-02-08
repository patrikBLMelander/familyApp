package com.familyapp.infrastructure.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChildWalletJpaRepository extends JpaRepository<ChildWalletEntity, UUID> {
    Optional<ChildWalletEntity> findByMemberId(UUID memberId);
}
