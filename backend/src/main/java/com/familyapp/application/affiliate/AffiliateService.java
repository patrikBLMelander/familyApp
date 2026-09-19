package com.familyapp.application.affiliate;

import com.familyapp.domain.affiliate.AffiliateStatus;
import com.familyapp.domain.affiliate.CommissionStatus;
import com.familyapp.domain.affiliate.ReferralSource;
import com.familyapp.infrastructure.affiliate.AffiliateCommissionJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliateEntity;
import com.familyapp.infrastructure.affiliate.AffiliateJpaRepository;
import com.familyapp.infrastructure.affiliate.AffiliateReferralEntity;
import com.familyapp.infrastructure.affiliate.AffiliateReferralJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Affiliate accounts, attribution and the read models the portal and admin views need.
 *
 * Invite-only: an admin {@link #createAffiliate} makes a row with no password; the affiliate
 * {@link #activate}s by choosing one, which only works because the row already exists.
 */
@Service
public class AffiliateService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final AffiliateJpaRepository affiliateRepository;
    private final AffiliateReferralJpaRepository referralRepository;
    private final AffiliateCommissionJpaRepository commissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Set<String> adminEmails;
    private final String linkBase;
    private final BigDecimal defaultCommissionPct;
    private final int defaultMonthCap;

    public AffiliateService(
            AffiliateJpaRepository affiliateRepository,
            AffiliateReferralJpaRepository referralRepository,
            AffiliateCommissionJpaRepository commissionRepository,
            PasswordEncoder passwordEncoder,
            @Value("${kidquest.affiliate.admin-emails:}") String adminEmailsCsv,
            @Value("${kidquest.affiliate.link-base:https://www.kidquest.se/?ref=}") String linkBase,
            @Value("${kidquest.affiliate.commission-pct:20.00}") BigDecimal defaultCommissionPct,
            @Value("${kidquest.affiliate.month-cap:12}") int defaultMonthCap
    ) {
        this.affiliateRepository = affiliateRepository;
        this.referralRepository = referralRepository;
        this.commissionRepository = commissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.linkBase = linkBase;
        this.defaultCommissionPct = defaultCommissionPct;
        this.defaultMonthCap = defaultMonthCap;
    }

    /** True if this email is configured as an affiliate-program admin. */
    public boolean isAdmin(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    // ---- Admin ---------------------------------------------------------------

    /** Invite an affiliate: creates the row with no password. They activate later. */
    @Transactional
    public AffiliateAdminRow createAffiliate(String name, String email, String requestedCode) {
        var cleanEmail = requireText(email, "email").toLowerCase(Locale.ROOT);
        if (affiliateRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("An affiliate with that email already exists");
        }
        var code = (requestedCode == null || requestedCode.isBlank())
                ? generateUniqueCode()
                : normaliseCode(requestedCode);
        if (affiliateRepository.existsByReferralCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("That referral code is taken");
        }
        var now = OffsetDateTime.now();
        var entity = new AffiliateEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(requireText(name, "name"));
        entity.setEmail(cleanEmail);
        entity.setReferralCode(code);
        entity.setCommissionPct(defaultCommissionPct);
        entity.setCommissionMonthCap(defaultMonthCap);
        entity.setStatus(AffiliateStatus.INVITED.name());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        affiliateRepository.save(entity);
        log.info("Invited affiliate {} ({}) with code {}", entity.getId(), cleanEmail, code);
        return adminRow(entity);
    }

    /** Every affiliate with their referral count and payable amount, for the admin view. */
    @Transactional(readOnly = true)
    public List<AffiliateAdminRow> listAffiliates() {
        return affiliateRepository.findAll().stream().map(this::adminRow).toList();
    }

    // ---- Affiliate auth ------------------------------------------------------

    /** Set a password on an invited row. Fails if no invite exists or one is already active. */
    @Transactional
    public AffiliateSession activate(String email, String password) {
        var affiliate = affiliateRepository.findByEmailIgnoreCase(requireText(email, "email"))
                .orElseThrow(() -> new IllegalArgumentException("No invite for that email"));
        if (affiliate.getPasswordHash() != null) {
            throw new IllegalArgumentException("This account is already activated");
        }
        affiliate.setPasswordHash(passwordEncoder.encode(requireText(password, "password")));
        affiliate.setStatus(AffiliateStatus.ACTIVE.name());
        return issueSession(affiliate);
    }

    /** Verify credentials and issue a fresh portal token. */
    @Transactional
    public AffiliateSession login(String email, String password) {
        var affiliate = affiliateRepository.findByEmailIgnoreCase(requireText(email, "email"))
                .filter(a -> a.getPasswordHash() != null)
                .filter(a -> passwordEncoder.matches(password == null ? "" : password, a.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Wrong email or password"));
        if (AffiliateStatus.PAUSED.name().equalsIgnoreCase(affiliate.getStatus())) {
            throw new IllegalArgumentException("This account is paused");
        }
        return issueSession(affiliate);
    }

    /** Resolve a portal token to its affiliate, or throw. */
    @Transactional(readOnly = true)
    public AffiliateEntity authenticate(String token) {
        return affiliateRepository.findBySessionTokenHash(sha256(requireText(token, "token")))
                .orElseThrow(() -> new IllegalArgumentException("Invalid affiliate token"));
    }

    // ---- Attribution ---------------------------------------------------------

    /** Attribute a family to the affiliate owning {@code code}. First-touch: a claimed family is left alone. */
    @Transactional
    public void redeem(UUID familyId, String code) {
        if (familyId == null) {
            throw new IllegalArgumentException("familyId is required");
        }
        if (referralRepository.existsByFamilyId(familyId)) {
            log.info("Family {} already attributed; redeem ignored", familyId);
            return;
        }
        var affiliate = affiliateRepository.findByReferralCodeIgnoreCase(normaliseCode(requireText(code, "code")))
                .filter(a -> !AffiliateStatus.PAUSED.name().equalsIgnoreCase(a.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown referral code"));
        var referral = new AffiliateReferralEntity();
        referral.setId(UUID.randomUUID());
        referral.setAffiliateId(affiliate.getId());
        referral.setFamilyId(familyId);
        referral.setSource(ReferralSource.CODE.name());
        referral.setAttributedAt(OffsetDateTime.now());
        referralRepository.save(referral);
        log.info("Family {} attributed to affiliate {} via code", familyId, affiliate.getId());
    }

    // ---- Affiliate self view -------------------------------------------------

    /** The affiliate's own dashboard numbers. */
    @Transactional(readOnly = true)
    public AffiliateSelf selfView(AffiliateEntity affiliate) {
        return new AffiliateSelf(
                affiliate.getName(),
                affiliate.getReferralCode(),
                linkBase + affiliate.getReferralCode(),
                affiliate.getCommissionPct(),
                referralRepository.countByAffiliateId(affiliate.getId()),
                sum(affiliate.getId(), CommissionStatus.PENDING),
                sum(affiliate.getId(), CommissionStatus.APPROVED),
                sum(affiliate.getId(), CommissionStatus.PAID)
        );
    }

    // ---- helpers -------------------------------------------------------------

    private AffiliateSession issueSession(AffiliateEntity affiliate) {
        var raw = randomToken();
        affiliate.setSessionTokenHash(sha256(raw));
        affiliate.setUpdatedAt(OffsetDateTime.now());
        affiliateRepository.save(affiliate);
        return new AffiliateSession(raw, affiliate.getReferralCode(), affiliate.getName());
    }

    private AffiliateAdminRow adminRow(AffiliateEntity a) {
        return new AffiliateAdminRow(
                a.getId(),
                a.getName(),
                a.getEmail(),
                a.getReferralCode(),
                a.getStatus(),
                referralRepository.countByAffiliateId(a.getId()),
                sum(a.getId(), CommissionStatus.APPROVED),   // payable now
                sum(a.getId(), CommissionStatus.PENDING),
                sum(a.getId(), CommissionStatus.PAID)
        );
    }

    private BigDecimal sum(UUID affiliateId, CommissionStatus status) {
        var value = commissionRepository.sumAmountByAffiliateIdAndStatus(affiliateId, status.name());
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            var code = randomCode(8);
            if (!affiliateRepository.existsByReferralCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique referral code");
    }

    private static String randomCode(int len) {
        var sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String normaliseCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String randomToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    // ---- read models ---------------------------------------------------------

    /** Returned on activate/login: the portal token plus a little context. */
    public record AffiliateSession(String token, String referralCode, String name) {}

    /** The affiliate's own dashboard. */
    public record AffiliateSelf(
            String name,
            String referralCode,
            String referralLink,
            BigDecimal commissionPct,
            long referralCount,
            BigDecimal pending,
            BigDecimal approved,
            BigDecimal paidOut
    ) {}

    /** One row of the admin's affiliate list. */
    public record AffiliateAdminRow(
            UUID id,
            String name,
            String email,
            String referralCode,
            String status,
            long referralCount,
            BigDecimal payable,
            BigDecimal pending,
            BigDecimal paidOut
    ) {}
}
