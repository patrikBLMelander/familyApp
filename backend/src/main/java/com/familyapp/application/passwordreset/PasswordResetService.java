package com.familyapp.application.passwordreset;

import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.email.ResendEmailSender;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.passwordreset.PasswordResetTokenEntity;
import com.familyapp.infrastructure.passwordreset.PasswordResetTokenJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Forgotten passwords, without telling the internet who has an account.
 *
 * Two rules shape almost everything here.
 *
 * First: a reset request answers identically whether or not the address exists. No
 * error, no different timing branch worth measuring, no "unknown e-mail" message. An
 * endpoint that says "no such account" is a free tool for working out which addresses
 * belong to a family app -- and this one is used by children's parents.
 *
 * Second: the token is a credential. It is generated from SecureRandom, stored only as
 * a SHA-256 hash, invalidated when a newer one is issued, single-use, and short-lived.
 * Anyone holding the e-mail can become that parent, so it is treated with the same care
 * as the password it replaces.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Long enough to act on across devices, short enough that a stale inbox is not a key. */
    private static final int TOKEN_TTL_MINUTES = 60;

    /**
     * How long before the same member may request again.
     *
     * Without this, anyone can bury a parent's inbox in reset mail simply by knowing
     * their address, and every one of those messages is a live credential.
     */
    private static final int REQUEST_COOLDOWN_MINUTES = 2;

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final FamilyMemberJpaRepository memberRepository;
    private final PasswordResetTokenJpaRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResendEmailSender emailSender;
    private final String resetUrlBase;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            FamilyMemberJpaRepository memberRepository,
            PasswordResetTokenJpaRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ResendEmailSender emailSender,
            @Value("${kidquest.password-reset.url-base:https://familyapp-frontend-production.up.railway.app/aterstall-losenord}")
            String resetUrlBase
    ) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.resetUrlBase = resetUrlBase;
    }

    /**
     * Sends a reset link, if there is anywhere to send one.
     *
     * Returns nothing on purpose. The caller has no way to distinguish a sent mail from
     * an unknown address, because neither has the API.
     */
    @Transactional
    public void request(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        var normalized = email.trim().toLowerCase(Locale.ROOT);
        var member = memberRepository.findByEmail(normalized).orElse(null);
        if (member == null) {
            log.info("Password reset requested for an address with no account");
            return;
        }
        // Children have no password to reset -- they authenticate with a device token.
        if (!Role.PARENT.name().equals(member.getRole()) && !Role.ASSISTANT.name().equals(member.getRole())) {
            log.info("Password reset requested for member {} which is not a parent", member.getId());
            return;
        }

        var now = OffsetDateTime.now();
        var last = tokenRepository.findFirstByMemberIdOrderByCreatedAtDesc(member.getId()).orElse(null);
        if (last != null && last.getCreatedAt().isAfter(now.minusMinutes(REQUEST_COOLDOWN_MINUTES))) {
            log.info("Password reset for member {} throttled", member.getId());
            return;
        }

        tokenRepository.invalidateOutstanding(member.getId(), now);

        var rawToken = newToken();
        var entity = new PasswordResetTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setMemberId(member.getId());
        entity.setTokenHash(sha256(rawToken));
        entity.setExpiresAt(now.plusMinutes(TOKEN_TTL_MINUTES));
        entity.setCreatedAt(now);
        tokenRepository.save(entity);

        var link = resetUrlBase + "?token=" + rawToken;
        emailSender.send(normalized, "Återställ ditt lösenord i KidQuest", emailBody(member.getName(), link));
        log.info("Password reset token issued for member {}", member.getId());
    }

    /**
     * Spends a token and sets the new password.
     *
     * @throws IllegalArgumentException with one message for every failure -- expired,
     *   already used, never existed. Distinguishing them would let someone probe which
     *   tokens are real.
     */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Reset link is invalid or has expired");
        }
        if (newPassword == null || newPassword.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }

        var token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Reset link is invalid or has expired"));

        var now = OffsetDateTime.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Reset link is invalid or has expired");
        }

        var member = memberRepository.findById(token.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Reset link is invalid or has expired"));

        // Trimmed before hashing, matching registration and login. A password stored
        // untrimmed while login compares the trimmed one is exactly the defect that
        // made some accounts permanently unusable earlier.
        member.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        member.setUpdatedAt(now);
        memberRepository.save(member);

        token.setUsedAt(now);
        tokenRepository.save(token);

        log.info("Password reset completed for member {}", member.getId());
    }

    /** 256 bits from SecureRandom, URL-safe so it survives being an e-mailed query parameter. */
    private String newToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }

    private static String emailBody(String name, String link) {
        return """
                <div style="font-family: system-ui, -apple-system, sans-serif; line-height: 1.6; color: #1C1917; max-width: 480px;">
                  <h2 style="font-size: 20px; margin-bottom: 8px;">Återställ ditt lösenord</h2>
                  <p>Hej %s,</p>
                  <p>Någon har begärt ett nytt lösenord till ditt KidQuest-konto. Klicka på knappen nedan för att välja ett nytt. Länken gäller i en timme.</p>
                  <p style="margin: 24px 0;">
                    <a href="%s" style="background: #0C4A6E; color: #ffffff; padding: 12px 20px; border-radius: 10px; text-decoration: none; font-weight: 600;">Välj nytt lösenord</a>
                  </p>
                  <p style="font-size: 13px; color: #57534E;">Var det inte du? Då behöver du inte göra någonting — ditt nuvarande lösenord fortsätter att gälla.</p>
                  <p style="font-size: 13px; color: #78716C;">Fungerar inte knappen? Kopiera den här länken:<br>%s</p>
                </div>
                """.formatted(escape(name), link, link);
    }

    /** The name comes from a text field a parent typed, so it does not go into HTML raw. */
    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
