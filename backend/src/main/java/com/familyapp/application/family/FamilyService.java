package com.familyapp.application.family;

import com.familyapp.domain.family.Family;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.family.FamilyEntity;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class FamilyService {

    private final FamilyJpaRepository familyRepository;
    private final FamilyMemberJpaRepository memberRepository;

    public FamilyService(
            FamilyJpaRepository familyRepository,
            FamilyMemberJpaRepository memberRepository
    ) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
    }

    public FamilyRegistrationResult registerFamily(String familyName, String adminName, String adminEmail) {
        var now = OffsetDateTime.now();
        
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
        
        // Generate device token for admin
        String deviceToken = UUID.randomUUID().toString();
        adminEntity.setDeviceToken(deviceToken);
        var savedAdmin = memberRepository.save(adminEntity);
        
        return new FamilyRegistrationResult(
                toDomain(savedFamily),
                toDomain(savedAdmin),
                deviceToken
        );
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

    public EmailLoginResult loginByEmail(String email) {
        var member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email"));
        
        // Only allow email login for PARENT role (admin)
        if (!Role.PARENT.name().equals(member.getRole())) {
            throw new IllegalArgumentException("Email login is only available for admin users");
        }
        
        // Generate a new device token for this login
        String deviceToken = UUID.randomUUID().toString();
        member.setDeviceToken(deviceToken);
        member.setUpdatedAt(OffsetDateTime.now());
        var saved = memberRepository.save(member);
        
        return new EmailLoginResult(
                toDomain(saved),
                deviceToken
        );
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

