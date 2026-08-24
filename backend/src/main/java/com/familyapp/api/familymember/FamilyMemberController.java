package com.familyapp.api.familymember;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.familymember.FamilyMember.Role;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/family-members")
public class FamilyMemberController {

    private final FamilyMemberService service;

    public FamilyMemberController(FamilyMemberService service) {
        this.service = service;
    }

    @GetMapping
    public List<FamilyMemberResponse> getAllMembers(
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // SECURITY: Device token is required - no token means no access
        if (deviceToken == null || deviceToken.isEmpty()) {
            return List.of();
        }
        
        UUID familyId;
        try {
            var member = service.getMemberByDeviceToken(deviceToken);
            familyId = member.familyId();
            
            // SECURITY: Double-check that familyId is not null
            if (familyId == null) {
                throw new IllegalArgumentException("Member has no family ID");
            }
        } catch (IllegalArgumentException e) {
            // Invalid token or member has no family, return empty list
            // This prevents unauthorized access to family members
            return List.of();
        }
        
        // SECURITY: familyId is guaranteed to be non-null at this point
        return service.getAllMembers(familyId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Resolves the caller. Mandatory for every mutating endpoint: these used to pass a
     * null requester when no token was supplied, and the service then skipped its
     * authorisation check entirely.
     */
    private UUID requireRequesterId(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        return service.getMemberByDeviceToken(deviceToken).id();
    }

    @GetMapping("/by-device-token/{deviceToken}")
    public FamilyMemberResponse getMemberByDeviceToken(@PathVariable("deviceToken") String deviceToken) {
        var member = service.getMemberByDeviceToken(deviceToken);
        return toResponse(member);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyMemberResponse createMember(
            @RequestBody CreateFamilyMemberRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        // Without a token this created a member belonging to no family at all -- junk
        // rows from an unauthenticated caller. And adding family members is a parent's
        // job; a child should not be able to.
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        var requester = service.getMemberByDeviceToken(deviceToken);
        if (requester.role() != com.familyapp.domain.familymember.FamilyMember.Role.PARENT) {
            throw new IllegalArgumentException("Only a parent can add family members");
        }
        if (requester.familyId() == null) {
            throw new IllegalArgumentException("Requester does not belong to a family");
        }
        var member = service.createMember(request.name(), request.role(), requester.familyId());
        return toResponse(member);
    }

    @PatchMapping("/{memberId}")
    public FamilyMemberResponse updateMember(
            @PathVariable("memberId") UUID memberId,
            @RequestBody UpdateFamilyMemberRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        var member = service.updateMember(memberId, request.name(), requireRequesterId(deviceToken));
        return toResponse(member);
    }

    @PatchMapping("/{memberId}/password")
    public FamilyMemberResponse updatePassword(
            @PathVariable("memberId") UUID memberId,
            @RequestBody UpdatePasswordRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = requireRequesterId(deviceToken);
        
        var member = service.updatePassword(memberId, request.password(), requesterId);
        return toResponse(member);
    }

    @PatchMapping("/{memberId}/email")
    public FamilyMemberResponse updateEmail(
            @PathVariable("memberId") UUID memberId,
            @RequestBody UpdateEmailRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = requireRequesterId(deviceToken);
        
        var member = service.updateEmail(memberId, request.email(), requesterId);
        return toResponse(member);
    }

    @PatchMapping("/{memberId}/menstrual-cycle-settings")
    public FamilyMemberResponse updateMenstrualCycleSettings(
            @PathVariable("memberId") UUID memberId,
            @RequestBody UpdateMenstrualCycleSettingsRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = requireRequesterId(deviceToken);
        
        var member = service.updateMenstrualCycleSettings(
                memberId,
                request.enabled(),
                request.isPrivate(),
                requesterId
        );
        return toResponse(member);
    }

    @PatchMapping("/{memberId}/pet-settings")
    public FamilyMemberResponse updatePetSettings(
            @PathVariable("memberId") UUID memberId,
            @RequestBody UpdatePetSettingsRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        UUID requesterId = requireRequesterId(deviceToken);
        
        var member = service.updatePetSettings(
                memberId,
                request.enabled(),
                requesterId
        );
        return toResponse(member);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMember(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        service.deleteMember(memberId, requireRequesterId(deviceToken));
    }

    @PostMapping("/{memberId}/generate-invite")
    public InviteTokenResponse generateInviteToken(
            @PathVariable("memberId") UUID memberId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        String token = service.generateInviteToken(memberId, requireRequesterId(deviceToken));
        return new InviteTokenResponse(token);
    }

    @PostMapping("/link-device-by-token")
    public FamilyMemberResponse linkDeviceByInviteToken(@RequestBody LinkDeviceByTokenRequest request) {
        try {
            var member = service.linkDeviceByInviteToken(request.inviteToken(), request.deviceToken());
            return toResponse(member);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid invite token or device token: " + e.getMessage(), e);
        }
    }

    private FamilyMemberResponse toResponse(FamilyMember member) {
        return new FamilyMemberResponse(
                member.id(),
                member.name(),
                member.deviceToken(),
                member.email(),
                member.role(),
                member.familyId() != null ? member.familyId().toString() : null,
                member.deviceToken() != null && !member.deviceToken().isEmpty(),
                member.menstrualCycleEnabled() != null ? member.menstrualCycleEnabled() : false,
                member.menstrualCyclePrivate() != null ? member.menstrualCyclePrivate() : true,
                member.petEnabled() != null ? member.petEnabled() : false
        );
    }

    public record CreateFamilyMemberRequest(
            @NotBlank(message = "Name is required")
            String name,
            Role role
    ) {
    }

    public record UpdateFamilyMemberRequest(
            @NotBlank(message = "Name is required")
            String name
    ) {
    }

    public record UpdatePasswordRequest(
            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    public record UpdateEmailRequest(
            String email  // Can be null/empty to remove email
    ) {
    }

    public record UpdateMenstrualCycleSettingsRequest(
            Boolean enabled,
            Boolean isPrivate
    ) {
    }

    public record UpdatePetSettingsRequest(
            Boolean enabled
    ) {
    }

    public record FamilyMemberResponse(
            UUID id,
            String name,
            String deviceToken,
            String email,
            Role role,
            String familyId,
            /**
             * Whether the member has a device paired, without handing out the token
             * that would let the caller become them. Clients only ever needed the
             * fact: web renders it as "Kopplad" / "Ej kopplad".
             */
            boolean hasPairedDevice,
            Boolean menstrualCycleEnabled,
            Boolean menstrualCyclePrivate,
            Boolean petEnabled
    ) {
    }

    public record InviteTokenResponse(String token) {
    }

    public record LinkDeviceRequest(String deviceToken) {
    }

    public record LinkDeviceByTokenRequest(String inviteToken, String deviceToken) {
    }
}

