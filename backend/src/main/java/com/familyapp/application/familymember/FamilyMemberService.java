package com.familyapp.application.familymember;

import com.familyapp.application.cache.CacheService;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class FamilyMemberService {

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
    @Cacheable(value = "familyMembers", key = "#familyId != null ? #familyId.toString() : 'all'")
    public List<FamilyMember> getAllMembers(UUID familyId) {
        // Optimized: Use query instead of fetching all and filtering
        if (familyId != null) {
            return repository.findByFamilyIdOrderByNameAsc(familyId).stream()
                    .map(this::toDomain)
                    .toList();
        } else {
            // Fallback for legacy code (should not happen in production)
            return repository.findAll().stream()
                    .map(this::toDomain)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "deviceTokens", key = "#deviceToken", unless = "#result == null")
    public FamilyMember getMemberByDeviceToken(String deviceToken) {
        return repository.findByDeviceToken(deviceToken)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found for device token"));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "members", key = "#memberId.toString()", unless = "#result == null")
    public FamilyMember getMemberById(UUID memberId) {
        return repository.findById(memberId)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
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
        cacheService.evictFamilyMembers(familyId);
        
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
    @CachePut(value = "members", key = "#memberId.toString()")
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
    @CachePut(value = "members", key = "#memberId.toString()")
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
        }
        
        entity.setEmail(email != null && !email.trim().isEmpty() ? email.trim() : null);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = repository.save(entity);
        var result = toDomain(saved);
        
        // Evict familyMembers cache for this specific family (targeted eviction)
        cacheService.evictFamilyMembers(familyId);
        
        return result;
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
    @CachePut(value = "members", key = "#memberId.toString()")
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

