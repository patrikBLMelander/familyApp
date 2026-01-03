package com.familyapp.application.familymember;

import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
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

    public FamilyMemberService(
            FamilyMemberJpaRepository repository,
            FamilyJpaRepository familyRepository
    ) {
        this.repository = repository;
        this.familyRepository = familyRepository;
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

