package com.familyapp.application.family;

import com.familyapp.domain.family.Family;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import com.familyapp.infrastructure.family.FamilyEntity;
import com.familyapp.infrastructure.family.FamilyJpaRepository;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class FamilyService {

    private final FamilyJpaRepository familyRepository;
    private final FamilyMemberJpaRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public FamilyService(
            FamilyJpaRepository familyRepository,
            FamilyMemberJpaRepository memberRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
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

    public EmailLoginResult loginByEmailAndPassword(String email, String password) {
        var member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email"));
        
        // Only allow email login for PARENT or ASSISTANT role
        if (!Role.PARENT.name().equals(member.getRole()) && !Role.ASSISTANT.name().equals(member.getRole())) {
            throw new IllegalArgumentException("Email login is only available for parent or assistant users");
        }
        
        // Check if password is set
        if (member.getPasswordHash() == null || member.getPasswordHash().isEmpty()) {
            throw new IllegalArgumentException("Password not set for this account. Please set a password first.");
        }
        
        // Verify password
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
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

