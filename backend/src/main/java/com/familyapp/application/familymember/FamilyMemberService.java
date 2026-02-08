package com.familyapp.application.familymember;

import com.familyapp.application.cache.CacheService;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FamilyMemberService {

    private static final Logger log = LoggerFactory.getLogger(FamilyMemberService.class);

    private final FamilyMemberJpaRepository repository;
    private final FamilyJpaRepository familyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheService cacheService;

    public FamilyMemberService(
            FamilyMemberJpaRepository repository,
            FamilyJpaRepository familyRepository,
            PasswordEncoder passwordEncoder,
            CacheService cacheService
    ) {
        this.repository = repository;
        this.familyRepository = familyRepository;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "familyMembers", key = "#familyId.toString()", condition = "#familyId != null")
    public List<FamilyMember> getAllMembers(UUID familyId) {
        // SECURITY: familyId must never be null - this would expose all families' members
        if (familyId == null) {
            log.error("CRITICAL SECURITY ISSUE: getAllMembers called with null familyId - returning empty list");
            return List.of();
        }
        
        // Optimized: Use query instead of fetching all and filtering
        return repository.findByFamilyIdOrderByNameAsc(familyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "deviceTokens", key = "#p0", unless = "#result == null || #p0 == null || #p0.isEmpty()")
    public FamilyMember getMemberByDeviceToken(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token cannot be null or empty");
        }
        // Always fetch from database to ensure we have the latest data
        // Cache is used for performance, but we validate against DB
        var member = repository.findByDeviceToken(deviceToken)
                .map(this::toDomain)
                .orElseThrow(() -> {
                    // Evict from cache if member not found (prevents stale cache)
                    cacheService.evictDeviceToken(deviceToken);
                    return new IllegalArgumentException("Family member not found for device token");
                });
        
        // Double-check: If cached member exists but doesn't match DB, evict cache
        // This prevents cache poisoning where wrong user is returned
        try {
            var cachedMember = cacheService.getDeviceToken(deviceToken);
            if (cachedMember != null && !cachedMember.id().equals(member.id())) {
                // Cache contains wrong user - evict it
                log.warn("Cache mismatch detected for device token: {} - cached user: {}, DB user: {}. Evicting cache.",
                    deviceToken, cachedMember.id(), member.id());
                cacheService.evictDeviceToken(deviceToken);
            }
        } catch (Exception e) {
            // If cache lookup fails, continue with DB result
            log.debug("Cache lookup failed for device token: {}", deviceToken);
        }
        
        return member;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "members", key = "#memberId != null ? #memberId.toString() : 'null'", unless = "#result == null || #memberId == null")
    public FamilyMember getMemberById(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID cannot be null");
        }
        return repository.findById(memberId)
                .map(this::toDomain)
                .orElseThrow(() -> {
                    // Evict from cache if member doesn't exist (prevents caching null)
                    cacheService.evictMember(memberId);
                    return new IllegalArgumentException("Family member not found: " + memberId);
                });
    }

    /**
     * Creates a new family member.
     * 
     * Cache behavior:
     * - Caches the new member in "members" cache
     * - Evicts "familyMembers" cache for this member's family
     * 
     * @param name The member name
     * @param role The member role
     * @param familyId The family ID
     * @return The created member
     */
    public FamilyMember createMember(String name, Role role, UUID familyId) {
        var now = OffsetDateTime.now();
        var entity = new FamilyMemberEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setRole(role != null ? role.name() : Role.CHILD.name());
        
        // Set family
        if (familyId != null) {
            var family = familyRepository.findById(familyId)
                    .orElseThrow(() -> new IllegalArgumentException("Family not found: " + familyId));
            entity.setFamily(family);
        }
        
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Cache the new member
        cacheService.putMember(result.id(), result);
        
        // Evict familyMembers cache for this specific family (targeted eviction)
        // Also evict "all" cache entry to ensure consistency
        cacheService.evictFamilyMembers(familyId);
        cacheService.evictFamilyMembers(null); // Evict "all" cache entry
        
        log.debug("Created new family member: {} (ID: {}) in family: {}. Cache evicted.", 
            name, result.id(), familyId);
        
        return result;
    }

    /**
     * Updates a member's name.
     * 
     * Cache behavior:
     * - Updates "members" cache with new member data (via @CachePut)
     * - Evicts "familyMembers" cache for this member's family
     * - Does NOT evict "deviceTokens" cache (token unchanged)
     * 
     * @param memberId The member to update
     * @param name The new name
     * @return Updated member (cached)
     */
    @CachePut(value = "members", key = "#memberId != null ? #memberId.toString() : 'null'", unless = "#result == null || #memberId == null")
    public FamilyMember updateMember(UUID memberId, String name) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        UUID familyId = entity.getFamily() != null ? entity.getFamily().getId() : null;
        
        entity.setName(name);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Evict familyMembers cache for this specific family (targeted eviction)
        cacheService.evictFamilyMembers(familyId);
        
        return result;
    }

    /**
     * Updates a member's email.
     * 
     * Cache behavior:
     * - Updates "members" cache with new member data (via @CachePut)
     * - Evicts "familyMembers" cache for this member's family
     * 
     * @param memberId The member to update
     * @param email The new email
     * @param requesterId The requester ID (for permission check)
     * @return Updated member (cached)
     */
    @CachePut(value = "members", key = "#memberId != null ? #memberId.toString() : 'null'", unless = "#result == null || #memberId == null")
    public FamilyMember updateEmail(UUID memberId, String email, UUID requesterId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        UUID familyId = entity.getFamily() != null ? entity.getFamily().getId() : null;
        
        // Only allow email updates for PARENT or ASSISTANT role
        if (!Role.PARENT.name().equals(entity.getRole()) && !Role.ASSISTANT.name().equals(entity.getRole())) {
            throw new IllegalArgumentException("Email can only be set for parent or assistant users");
        }
        
        // Validate that requester is in the same family and has permission
        if (requesterId != null) {
            var requester = repository.findById(requesterId)
                    .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
            
            // Requester must be in the same family
            if (entity.getFamily() == null || requester.getFamily() == null ||
                !entity.getFamily().getId().equals(requester.getFamily().getId())) {
                throw new IllegalArgumentException("Cannot update email for member in different family");
            }
            
            // Requester must be PARENT, or ASSISTANT updating their own email
            boolean isParent = Role.PARENT.name().equals(requester.getRole());
            boolean isAssistantUpdatingSelf = Role.ASSISTANT.name().equals(requester.getRole()) && 
                                             requester.getId().equals(memberId);
            
            if (!isParent && !isAssistantUpdatingSelf) {
                throw new IllegalArgumentException("Only parents can update email for others, or assistants can update their own");
            }
        }
        
        // Validate email format (basic check)
        if (email != null && !email.trim().isEmpty()) {
            if (!email.contains("@") || !email.contains(".")) {
                throw new IllegalArgumentException("Invalid email format");
            }
            
            // Check if email is already in use by another member
            String trimmedEmail = email.trim();
            var existingMember = repository.findByEmail(trimmedEmail);
            if (existingMember.isPresent() && !existingMember.get().getId().equals(memberId)) {
                throw new IllegalArgumentException("Email is already in use by another account");
            }
        }
        
        entity.setEmail(email != null && !email.trim().isEmpty() ? email.trim() : null);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        try {
            var saved = repository.save(entity);
            var result = toDomain(saved);
            
            // Evict familyMembers cache for this specific family (targeted eviction)
            cacheService.evictFamilyMembers(familyId);
            
            return result;
        } catch (DataIntegrityViolationException e) {
            // Handle unique constraint violation (email already exists)
            if (e.getMessage() != null && e.getMessage().contains("email")) {
                throw new IllegalArgumentException("Email is already in use by another account");
            }
            throw e;
        }
    }

    /**
     * Updates a member's password.
     * 
     * Cache behavior:
     * - Updates "members" cache with new member data (via @CachePut)
     * - Evicts "familyMembers" cache for this member's family
     * 
     * @param memberId The member to update
     * @param newPassword The new password
     * @param requesterId The requester ID (for permission check)
     * @return Updated member (cached)
     */
    @CachePut(value = "members", key = "#memberId != null ? #memberId.toString() : 'null'", unless = "#result == null || #memberId == null")
    public FamilyMember updatePassword(UUID memberId, String newPassword, UUID requesterId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        UUID familyId = entity.getFamily() != null ? entity.getFamily().getId() : null;
        
        // Only allow password updates for PARENT or ASSISTANT role
        if (!Role.PARENT.name().equals(entity.getRole()) && !Role.ASSISTANT.name().equals(entity.getRole())) {
            throw new IllegalArgumentException("Password can only be set for parent or assistant users");
        }
        
        // Validate that requester is in the same family and has permission
        if (requesterId != null) {
            var requester = repository.findById(requesterId)
                    .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
            
            // Requester must be in the same family
            if (entity.getFamily() == null || requester.getFamily() == null ||
                !entity.getFamily().getId().equals(requester.getFamily().getId())) {
                throw new IllegalArgumentException("Cannot update password for member in different family");
            }
            
            // Requester must be PARENT, or ASSISTANT updating their own password
            boolean isParent = Role.PARENT.name().equals(requester.getRole());
            boolean isAssistantUpdatingSelf = Role.ASSISTANT.name().equals(requester.getRole()) && 
                                             requester.getId().equals(memberId);
            
            if (!isParent && !isAssistantUpdatingSelf) {
                throw new IllegalArgumentException("Only parents can update passwords for others, or assistants can update their own");
            }
        }
        
        // Validate password
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        
        // Hash and set password
        String hashedPassword = passwordEncoder.encode(newPassword);
        entity.setPasswordHash(hashedPassword);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Evict familyMembers cache for this specific family (targeted eviction)
        cacheService.evictFamilyMembers(familyId);
        
        return result;
    }

    /**
     * Deletes a member.
     * 
     * Cache behavior:
     * - Evicts "members" cache for this member
     * - Evicts "deviceTokens" cache for this member's token
     * - Evicts "familyMembers" cache for this member's family
     * 
     * @param memberId The member to delete
     */
    public void deleteMember(UUID memberId) {
        // Prevent deletion of admin user
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (adminId.equals(memberId)) {
            throw new IllegalArgumentException("Cannot delete admin user");
        }
        
        // Get member before deletion to evict cache
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        UUID familyId = entity.getFamily() != null ? entity.getFamily().getId() : null;
        String deviceToken = entity.getDeviceToken();
        
        repository.deleteById(memberId);
        
        // Evict caches (targeted eviction)
        cacheService.evictMember(memberId);
        cacheService.evictDeviceToken(deviceToken);
        cacheService.evictFamilyMembers(familyId);
    }

    /**
     * Generates a new invite token for a member.
     * 
     * Cache behavior:
     * - Evicts old device token from cache
     * - Caches updated member in "members" cache
     * - Caches new token in "deviceTokens" cache
     * 
     * @param memberId The member to generate token for
     * @return The new invite token
     */
    public String generateInviteToken(UUID memberId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        // Get old token to evict from cache
        String oldToken = entity.getDeviceToken();
        
        // Generate a unique token (UUID as string)
        String token = UUID.randomUUID().toString();
        entity.setDeviceToken(token);
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        
        var member = toDomain(entity);
        
        // Evict old device token from cache (targeted eviction)
        cacheService.evictDeviceToken(oldToken);
        
        // Cache updated member and new token
        cacheService.putMember(memberId, member);
        cacheService.putDeviceToken(token, member);
        
        return token;
    }

    /**
     * Links a device token to a member.
     * 
     * Cache behavior:
     * - Evicts old device token from cache (if different)
     * - Caches updated member in "members" cache
     * - Caches new token in "deviceTokens" cache
     * 
     * @param deviceToken The device token to link
     * @param memberId The member to link to
     * @return Updated member (cached)
     */
    public FamilyMember linkDeviceToMember(String deviceToken, UUID memberId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        // Check if token is already used by another member
        repository.findByDeviceToken(deviceToken).ifPresent(existing -> {
            if (!existing.getId().equals(memberId)) {
                throw new IllegalArgumentException("Device token already in use");
            }
        });
        
        // Get old token to evict from cache
        String oldToken = entity.getDeviceToken();
        
        entity.setDeviceToken(deviceToken);
        entity.setUpdatedAt(OffsetDateTime.now());
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Evict old device token from cache (targeted eviction)
        if (oldToken != null && !oldToken.equals(deviceToken)) {
            cacheService.evictDeviceToken(oldToken);
        }
        
        // Cache updated member and new token
        cacheService.putMember(memberId, result);
        cacheService.putDeviceToken(deviceToken, result);
        
        return result;
    }

    /**
     * Links a device to a member using an invite token.
     * 
     * Cache behavior:
     * - Evicts old device token from cache (if different)
     * - Caches updated member in "members" cache
     * - Caches new token in "deviceTokens" cache
     * 
     * @param inviteToken The invite token
     * @param deviceToken The device token to link
     * @return Updated member (cached)
     */
    public FamilyMember linkDeviceByInviteToken(String inviteToken, String deviceToken) {
        // Find member by invite token (which is stored as deviceToken)
        var entity = repository.findByDeviceToken(inviteToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));
        
        // Check if deviceToken is already used by another member
        repository.findByDeviceToken(deviceToken).ifPresent(existing -> {
            if (!existing.getId().equals(entity.getId())) {
                throw new IllegalArgumentException("Device token already in use");
            }
        });
        
        // Get old token to evict from cache
        String oldToken = entity.getDeviceToken();
        
        // Link the device to the member
        entity.setDeviceToken(deviceToken);
        entity.setUpdatedAt(OffsetDateTime.now());
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Evict old device token from cache (targeted eviction)
        if (oldToken != null && !oldToken.equals(deviceToken)) {
            cacheService.evictDeviceToken(oldToken);
        }
        
        // Cache updated member and new token
        cacheService.putMember(result.id(), result);
        cacheService.putDeviceToken(deviceToken, result);
        
        return result;
    }

    /**
     * Updates menstrual cycle settings for a member.
     * 
     * Cache behavior:
     * - Updates "members" cache with new member data (via @CachePut)
     * - Evicts "familyMembers" cache for this member's family
     * 
     * @param memberId The member to update
     * @param enabled Whether menstrual cycle tracking is enabled
     * @param isPrivate Whether the data is private (true) or shared with other adults (false)
     * @param requesterId The requester ID (for permission check)
     * @return Updated member (cached)
     */
    @CachePut(value = "members", key = "#memberId != null ? #memberId.toString() : 'null'", unless = "#result == null || #memberId == null")
    public FamilyMember updateMenstrualCycleSettings(UUID memberId, Boolean enabled, Boolean isPrivate, UUID requesterId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        UUID familyId = entity.getFamily() != null ? entity.getFamily().getId() : null;
        
        // Only allow menstrual cycle settings for PARENT or ASSISTANT role
        if (!Role.PARENT.name().equals(entity.getRole()) && !Role.ASSISTANT.name().equals(entity.getRole())) {
            throw new IllegalArgumentException("Menstrual cycle tracking can only be enabled for parent or assistant users");
        }
        
        // Validate that requester is in the same family and has permission
        if (requesterId != null) {
            var requester = repository.findById(requesterId)
                    .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
            
            // Requester must be in the same family
            if (entity.getFamily() == null || requester.getFamily() == null ||
                !entity.getFamily().getId().equals(requester.getFamily().getId())) {
                throw new IllegalArgumentException("Cannot update menstrual cycle settings for member in different family");
            }
            
            // Requester must be PARENT, or ASSISTANT updating their own settings
            boolean isParent = Role.PARENT.name().equals(requester.getRole());
            boolean isAssistantUpdatingSelf = Role.ASSISTANT.name().equals(requester.getRole()) && 
                                             requester.getId().equals(memberId);
            
            if (!isParent && !isAssistantUpdatingSelf) {
                throw new IllegalArgumentException("Only parents can update menstrual cycle settings for others, or assistants can update their own");
            }
        }
        
        entity.setMenstrualCycleEnabled(enabled != null ? enabled : false);
        entity.setMenstrualCyclePrivate(isPrivate != null ? isPrivate : true);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Evict familyMembers cache for this specific family (targeted eviction)
        cacheService.evictFamilyMembers(familyId);
        
        return result;
    }

    /**
     * Updates pet settings for a member.
     * 
     * Cache behavior:
     * - Updates "members" cache with new member data (via @CachePut)
     * - Evicts "familyMembers" cache for this member's family
     * 
     * @param memberId The member to update
     * @param enabled Whether pets are enabled
     * @param requesterId The requester ID (for permission check)
     * @return Updated member (cached)
     */
    @CachePut(value = "members", key = "#memberId != null ? #memberId.toString() : 'null'", unless = "#result == null || #memberId == null")
    public FamilyMember updatePetSettings(UUID memberId, Boolean enabled, UUID requesterId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        UUID familyId = entity.getFamily() != null ? entity.getFamily().getId() : null;
        
        // Only allow pet settings for PARENT role
        if (!Role.PARENT.name().equals(entity.getRole())) {
            throw new IllegalArgumentException("Pets can only be enabled for parent users");
        }
        
        // Validate that requester is in the same family and has permission
        if (requesterId != null) {
            var requester = repository.findById(requesterId)
                    .orElseThrow(() -> new IllegalArgumentException("Requester not found"));
            
            // Requester must be in the same family
            if (entity.getFamily() == null || requester.getFamily() == null ||
                !entity.getFamily().getId().equals(requester.getFamily().getId())) {
                throw new IllegalArgumentException("Cannot update pet settings for member in different family");
            }
            
            // Requester must be PARENT, or updating their own settings
            boolean isParent = Role.PARENT.name().equals(requester.getRole());
            boolean isUpdatingSelf = requester.getId().equals(memberId);
            
            if (!isParent && !isUpdatingSelf) {
                throw new IllegalArgumentException("Only parents can update pet settings");
            }
        }
        
        entity.setPetEnabled(enabled != null ? enabled : false);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Evict caches to ensure fresh data is returned
        // Evict deviceTokens cache so getMemberByDeviceToken returns updated data
        String deviceToken = entity.getDeviceToken();
        if (deviceToken != null && !deviceToken.isEmpty()) {
            cacheService.evictDeviceToken(deviceToken);
        }
        // Evict members cache
        cacheService.evictMember(memberId);
        // Evict familyMembers cache for this specific family (targeted eviction)
        cacheService.evictFamilyMembers(familyId);
        
        return result;
    }

    private FamilyMember toDomain(FamilyMemberEntity entity) {
        Role role = Role.CHILD;
        if (entity.getRole() != null) {
            try {
                role = Role.valueOf(entity.getRole());
            } catch (IllegalArgumentException e) {
                // Default to CHILD if invalid role
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
                entity.getPetEnabled() != null ? entity.getPetEnabled() : false,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

