package com.familyapp.api.admin;

import com.familyapp.application.affiliate.AffiliateService;
import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.familymember.FamilyMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Statistics and affiliate admin share one gate: signed in, and on the admin list. */
class AdminAccessTest {

    private FamilyMemberService members;
    private AffiliateService affiliates;
    private AdminAccess access;

    @BeforeEach
    void setUp() {
        members = mock(FamilyMemberService.class);
        affiliates = mock(AffiliateService.class);
        access = new AdminAccess(members, affiliates);
    }

    @Test
    void noTokenIsUnauthorized() {
        assertThatThrownBy(() -> access.requireAdmin(null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void aParentWhoIsNotAnAdminIsForbidden() {
        when(members.getMemberByDeviceToken("tok")).thenReturn(parent("someone@example.com"));
        when(affiliates.isAdmin("someone@example.com")).thenReturn(false);

        assertThatThrownBy(() -> access.requireAdmin("tok"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void anAdminGetsIn() {
        when(members.getMemberByDeviceToken("tok")).thenReturn(parent("admin@example.com"));
        when(affiliates.isAdmin("admin@example.com")).thenReturn(true);

        assertThatCode(() -> access.requireAdmin("tok")).doesNotThrowAnyException();
    }

    private static FamilyMember parent(String email) {
        return new FamilyMember(UUID.randomUUID(), "n", "tok", email, FamilyMember.Role.PARENT,
                UUID.randomUUID(), false, null, null, null);
    }
}
