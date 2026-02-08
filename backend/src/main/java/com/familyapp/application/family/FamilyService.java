package com.familyapp.application.family;

import com.familyapp.application.cache.CacheService;
import com.familyapp.domain.family.Family;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.family.FamilyEntity;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class FamilyService {

    private static final Logger log = LoggerFactory.getLogger(FamilyService.class);

    private final FamilyJpaRepository familyRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheService cacheService;

    public FamilyService(
            FamilyJpaRepository familyRepository,
            FamilyMemberJpaRepository memberRepository,
            PasswordEncoder passwordEncoder,
            CacheService cacheService
    ) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    public FamilyRegistrationResult registerFamily(String familyName, String adminName, String adminEmail, String password) {
        var now = OffsetDateTime.now();
        
        // Validate password
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        
        // Create family
        var familyEntity = new FamilyEntity();
        familyEntity.setId(UUID.randomUUID());
        familyEntity.setName(familyName);
        familyEntity.setCreatedAt(now);
        familyEntity.setUpdatedAt(now);
        var savedFamily = familyRepository.save(familyEntity);
        
        // Create admin user
        var adminEntity = new FamilyMemberEntity();
        adminEntity.setId(UUID.randomUUID());
        adminEntity.setName(adminName);
        adminEntity.setEmail(adminEmail);
        adminEntity.setRole(Role.PARENT.name());
        adminEntity.setFamily(savedFamily);
        adminEntity.setCreatedAt(now);
        adminEntity.setUpdatedAt(now);
        
        // Hash password
        String hashedPassword = passwordEncoder.encode(password);
        adminEntity.setPasswordHash(hashedPassword);
        
        // Generate unique device token for admin
        // Retry if token collision occurs (extremely rare but possible)
        String deviceToken;
        int maxRetries = 10;
        int retries = 0;
        do {
            deviceToken = UUID.randomUUID().toString();
            // Check if token already exists in database
            var existingMember = memberRepository.findByDeviceToken(deviceToken);
            if (existingMember.isEmpty()) {
                // Token is unique, break out of loop
                break;
            }
            retries++;
            if (retries >= maxRetries) {
                throw new IllegalStateException("Failed to generate unique device token after " + maxRetries + " attempts");
            }
            log.warn("Device token collision detected, retrying... (attempt {}/{})", retries, maxRetries);
        } while (retries < maxRetries);
        
        // Evict any stale cache entry for this token (defensive)
        cacheService.evictDeviceToken(deviceToken);
        
        adminEntity.setDeviceToken(deviceToken);
        var savedAdmin = memberRepository.save(adminEntity);
        
        var adminMember = toDomain(savedAdmin);
        var result = new FamilyRegistrationResult(
                toDomain(savedFamily),
                adminMember,
                deviceToken
        );
        
        // Evict familyMembers cache for this new family (targeted eviction)
        cacheService.evictFamilyMembers(savedFamily.getId());
        
        // Cache the new member and device token
        cacheService.putMember(savedAdmin.getId(), adminMember);
        cacheService.putDeviceToken(deviceToken, adminMember);
        
        return result;
    }

    @Transactional(readOnly = true)
    public Family getFamilyById(UUID familyId) {
        return familyRepository.findById(familyId)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Family not found: " + familyId));
    }

    public Family updateFamilyName(UUID familyId, String name) {
        var entity = familyRepository.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("Family not found: " + familyId));
        entity.setName(name);
        entity.setUpdatedAt(OffsetDateTime.now());
        var saved = familyRepository.save(entity);
        return toDomain(saved);
    }

    /**
     * Logs in a user with email and password.
     * 
     * Cache behavior:
     * - Evicts old device token from cache
     * - Caches new member and device token
     * 
     * @param email The user's email
     * @param password The user's password
     * @return Login result with member and new device token
     * @throws IllegalArgumentException if email not found, password invalid, or user not authorized
     */
    public EmailLoginResult loginByEmailAndPassword(String email, String password) {
        // Validate input
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        // Normalize input (trim whitespace)
        String normalizedEmail = email.trim();
        String normalizedPassword = password.trim();
        
        // Find member by email (not cached - always fresh from database)
        var member = memberRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email"));
        
        // Only allow email login for PARENT or ASSISTANT role
        if (!Role.PARENT.name().equals(member.getRole()) && !Role.ASSISTANT.name().equals(member.getRole())) {
            throw new IllegalArgumentException("Email login is only available for parent or assistant users");
        }
        
        // Check if password is set
        String passwordHash = member.getPasswordHash();
        if (passwordHash == null || passwordHash.isEmpty()) {
            throw new IllegalArgumentException("Password not set for this account. Please set a password first.");
        }
        
        // Verify password
        // Note: passwordEncoder.matches() handles BCrypt verification
        // It returns false if password doesn't match the hash
        // BCrypt hashes always start with $2a$, $2b$, or $2y$
        // Use normalized password (trimmed) to avoid whitespace issues
        boolean passwordMatches = passwordEncoder.matches(normalizedPassword, passwordHash);
        if (!passwordMatches) {
            // Log for debugging (without exposing sensitive data)
            if (passwordHash != null && passwordHash.length() > 0) {
                String hashPrefix = passwordHash.length() > 4 ? passwordHash.substring(0, 4) : "unknown";
                // Only log hash prefix for debugging, not the full hash
                // Log password length (not content) to help diagnose whitespace issues
                log.warn("Password verification failed for email: {}, hash prefix: {}, hash length: {}, password length: {}", 
                    normalizedEmail, hashPrefix, passwordHash.length(), normalizedPassword.length());
            } else {
                log.warn("Password verification failed for email: {}, password hash is null or empty", normalizedEmail);
            }
            throw new IllegalArgumentException("Invalid password");
        }
        
        // Get old token to evict from cache
        String oldToken = member.getDeviceToken();
        
        // Generate unique new device token for login
        // Retry if token collision occurs (extremely rare but possible)
        String newDeviceToken;
        int maxRetries = 10;
        int retries = 0;
        do {
            newDeviceToken = UUID.randomUUID().toString();
            // Check if token already exists in database (and is not the old token)
            var existingMember = memberRepository.findByDeviceToken(newDeviceToken);
            if (existingMember.isEmpty() || (oldToken != null && newDeviceToken.equals(oldToken))) {
                // Token is unique, break out of loop
                break;
            }
            retries++;
            if (retries >= maxRetries) {
                throw new IllegalStateException("Failed to generate unique device token after " + maxRetries + " attempts");
            }
            log.warn("Device token collision detected during login, retrying... (attempt {}/{})", retries, maxRetries);
        } while (retries < maxRetries);
        
        // Evict any stale cache entry for the new token (defensive)
        cacheService.evictDeviceToken(newDeviceToken);
        
        member.setDeviceToken(newDeviceToken);
        member.setUpdatedAt(OffsetDateTime.now());
        memberRepository.save(member);
        
        var memberDomain = toDomain(member);
        var result = new EmailLoginResult(
                memberDomain,
                newDeviceToken
        );
        
        // Evict old device token from cache (targeted eviction)
        cacheService.evictDeviceToken(oldToken);
        
        // Cache new member and device token
        cacheService.putMember(member.getId(), memberDomain);
        cacheService.putDeviceToken(newDeviceToken, memberDomain);
        
        return result;
    }

    private Family toDomain(FamilyEntity entity) {
        return new Family(
                entity.getId(),
                entity.getName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private FamilyMember toDomain(FamilyMemberEntity entity) {
        Role role = Role.CHILD;
        if (entity.getRole() != null) {
            try {
                role = Role.valueOf(entity.getRole());
            } catch (IllegalArgumentException e) {
                role = Role.CHILD;
            }
        }
        
        return new FamilyMember(
                entity.getId(),
                entity.getName(),
                entity.getDeviceToken(),
                entity.getEmail(),
                role,
                entity.getFamily() != null ? entity.getFamily().getId() : null,
                entity.getMenstrualCycleEnabled() != null ? entity.getMenstrualCycleEnabled() : false,
                entity.getMenstrualCyclePrivate() != null ? entity.getMenstrualCyclePrivate() : true,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public record FamilyRegistrationResult(
            Family family,
            FamilyMember admin,
            String deviceToken
    ) {
    }

    public record EmailLoginResult(
            FamilyMember member,
            String deviceToken
    ) {
    }
}

