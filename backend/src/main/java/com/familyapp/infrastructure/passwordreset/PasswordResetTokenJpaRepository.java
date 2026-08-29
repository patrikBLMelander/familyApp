package com.familyapp.infrastructure.passwordreset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    /** The rate limit: how recently did this member last ask? */
    Optional<PasswordResetTokenEntity> findFirstByMemberIdOrderByCreatedAtDesc(UUID memberId);

    /**
     * Burns every outstanding token for a member.
     *
     * Requesting a new link must void the old one, or an intercepted e-mail from last
     * week stays usable for as long as its expiry allows.
     */
    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.usedAt = :now "
            + "WHERE t.memberId = :memberId AND t.usedAt IS NULL")
    void invalidateOutstanding(@Param("memberId") UUID memberId, @Param("now") OffsetDateTime now);
}
