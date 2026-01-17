package com.familyapp.api.family;

import com.familyapp.application.family.FamilyService;
import com.familyapp.domain.family.Family;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {

    private final FamilyService service;

    public FamilyController(FamilyService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyRegistrationResponse register(@RequestBody RegisterFamilyRequest request) {
        var result = service.registerFamily(request.familyName(), request.adminName(), request.adminEmail(), request.password());
        return new FamilyRegistrationResponse(
                toResponse(result.family()),
                toResponse(result.admin()),
                result.deviceToken()
        );
    }

    @PostMapping("/login-by-email")
    public EmailLoginResponse loginByEmail(@RequestBody LoginByEmailRequest request) {
        var result = service.loginByEmailAndPassword(request.email(), request.password());
        return new EmailLoginResponse(
                toResponse(result.member()),
                result.deviceToken()
        );
    }

    @GetMapping("/{familyId}")
    public FamilyResponse getFamily(@PathVariable("familyId") UUID familyId) {
        var family = service.getFamilyById(familyId);
        return toResponse(family);
    }

    @PatchMapping("/{familyId}/name")
    public FamilyResponse updateFamilyName(
            @PathVariable("familyId") UUID familyId,
            @RequestBody UpdateFamilyNameRequest request
    ) {
        var family = service.updateFamilyName(familyId, request.name());
        return toResponse(family);
    }

    private FamilyResponse toResponse(Family family) {
        return new FamilyResponse(
                family.id(),
                family.name(),
                family.createdAt().toString(),
                family.updatedAt().toString()
        );
    }

    private FamilyMemberResponse toResponse(com.familyapp.domain.familymember.FamilyMember member) {
        return new FamilyMemberResponse(
                member.id(),
                member.name(),
                member.deviceToken(),
                member.email(),
                member.role()
        );
    }

    public record RegisterFamilyRequest(
            @NotBlank(message = "Family name is required")
            String familyName,
            @NotBlank(message = "Admin name is required")
            String adminName,
            @NotBlank(message = "Email is required")
            String adminEmail,
            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    public record LoginByEmailRequest(
            @NotBlank(message = "Email is required")
            String email,
            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    public record UpdateFamilyNameRequest(
            @NotBlank(message = "Name is required")
            String name
    ) {
    }

    public record FamilyResponse(
            UUID id,
            String name,
            String createdAt,
            String updatedAt
    ) {
    }

    public record FamilyMemberResponse(
            UUID id,
            String name,
            String deviceToken,
            String email,
            com.familyapp.domain.familymember.FamilyMember.Role role
    ) {
    }

    public record FamilyRegistrationResponse(
            FamilyResponse family,
            FamilyMemberResponse admin,
            String deviceToken
    ) {
    }

    public record EmailLoginResponse(
            FamilyMemberResponse member,
            String deviceToken
    ) {
    }
}

