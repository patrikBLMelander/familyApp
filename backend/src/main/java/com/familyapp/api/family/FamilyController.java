package com.familyapp.api.family;

import com.familyapp.application.family.FamilyService;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.family.Family;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {

    private final FamilyService service;
    private final FamilyMemberService memberService;

    public FamilyController(FamilyService service, FamilyMemberService memberService) {
        this.service = service;
        this.memberService = memberService;
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

    /**
     * The family's own name. Read by the app to title the parent's overview.
     *
     * Took no device token until the app had a reason to call it: a family id was
     * enough to read the name of any household in the database. Same shape as the
     * thirteen endpoints already closed -- a missing token skipped the check rather
     * than failing it, because there was no check.
     */
    @GetMapping("/{familyId}")
    public FamilyResponse getFamily(
            @PathVariable("familyId") UUID familyId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        requireMemberOf(familyId, deviceToken);
        return toResponse(service.getFamilyById(familyId));
    }

    /** Renaming is parent administration, and until now it took no token at all. */
    @PatchMapping("/{familyId}/name")
    public FamilyResponse updateFamilyName(
            @PathVariable("familyId") UUID familyId,
            @RequestBody UpdateFamilyNameRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        var requester = requireMemberOf(familyId, deviceToken);
        if (requester.role() != com.familyapp.domain.familymember.FamilyMember.Role.PARENT) {
            throw new IllegalArgumentException("Endast en förälder kan byta familjens namn");
        }
        return toResponse(service.updateFamilyName(familyId, request.name()));
    }

    /**
     * The caller must be in the family they are asking about. A child has a device
     * token too, so this is a membership check rather than a role check -- reading
     * the household's own name is not privileged within it.
     */
    private com.familyapp.domain.familymember.FamilyMember requireMemberOf(
            UUID familyId, String deviceToken
    ) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        var requester = memberService.getMemberByDeviceToken(deviceToken);
        if (!familyId.equals(requester.familyId())) {
            throw new IllegalArgumentException("Not a member of this family");
        }
        return requester;
    }

    /**
     * Deletes the caller's family and everything in it. Required by both stores.
     *
     * The device token identifies who is asking; the service checks they are a
     * parent of this family before touching anything.
     */
    @DeleteMapping("/{familyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFamily(
            @PathVariable("familyId") UUID familyId,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            throw new IllegalArgumentException("Device token is required");
        }
        var requester = memberService.getMemberByDeviceToken(deviceToken);
        service.deleteFamily(familyId, requester.id());
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
                member.role(),
                // Clients need this to identify the family to the purchase provider:
                // entitlement is bought once per household, not per member.
                member.familyId() != null ? member.familyId().toString() : null
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
            com.familyapp.domain.familymember.FamilyMember.Role role,
            String familyId
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

