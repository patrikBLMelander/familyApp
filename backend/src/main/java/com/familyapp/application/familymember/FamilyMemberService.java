package com.familyapp.application.familymember;

import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
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

    public FamilyMemberService(
            FamilyMemberJpaRepository repository,
            FamilyJpaRepository familyRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.familyRepository = familyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<FamilyMember> getAllMembers(UUID familyId) {
        return repository.findAll().stream()
                .filter(m -> m.getFamily() != null && m.getFamily().getId().equals(familyId))
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public FamilyMember getMemberByDeviceToken(String deviceToken) {
        return repository.findByDeviceToken(deviceToken)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found for device token"));
    }

    @Transactional(readOnly = true)
    public FamilyMember getMemberById(UUID memberId) {
        return repository.findById(memberId)
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
    }

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
        return toDomain(saved);
    }

    public FamilyMember updateMember(UUID memberId, String name) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        entity.setName(name);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        var saved = repository.save(entity);
        return toDomain(saved);
    }

    public FamilyMember updateEmail(UUID memberId, String email, UUID requesterId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
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
        return toDomain(saved);
    }

    public FamilyMember updatePassword(UUID memberId, String newPassword, UUID requesterId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
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
        return toDomain(saved);
    }

    public void deleteMember(UUID memberId) {
        // Prevent deletion of admin user
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (adminId.equals(memberId)) {
            throw new IllegalArgumentException("Cannot delete admin user");
        }
        repository.deleteById(memberId);
    }

    public String generateInviteToken(UUID memberId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        // Generate a unique token (UUID as string)
        String token = UUID.randomUUID().toString();
        entity.setDeviceToken(token);
        entity.setUpdatedAt(OffsetDateTime.now());
        repository.save(entity);
        
        return token;
    }

    public FamilyMember linkDeviceToMember(String deviceToken, UUID memberId) {
        var entity = repository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Family member not found: " + memberId));
        
        // Check if token is already used by another member
        repository.findByDeviceToken(deviceToken).ifPresent(existing -> {
            if (!existing.getId().equals(memberId)) {
                throw new IllegalArgumentException("Device token already in use");
            }
        });
        
        entity.setDeviceToken(deviceToken);
        entity.setUpdatedAt(OffsetDateTime.now());
        var saved = repository.save(entity);
        return toDomain(saved);
    }

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
        
        // Link the device to the member
        entity.setDeviceToken(deviceToken);
        entity.setUpdatedAt(OffsetDateTime.now());
        var saved = repository.save(entity);
        return toDomain(saved);
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

