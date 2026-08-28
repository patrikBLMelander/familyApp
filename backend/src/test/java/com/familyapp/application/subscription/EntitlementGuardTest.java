package com.familyapp.application.subscription;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.familymember.FamilyMember;
import com.familyapp.domain.subscription.SubscriptionRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What may be refused for non-payment, and what may never be.
 *
 * The second half matters more than the first. Over-blocking is invisible in testing
 * and lands on a child: a chore that will not tick, a pet that cannot be fed, a parent
 * locked out of the password screen they need in order to pay at all.
 */
class EntitlementGuardTest {

    private static final String TOKEN = "tok";
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID FAMILY = UUID.randomUUID();

    private FamilyMemberService members;
    private SubscriptionService subscriptions;
    private EntitlementGuard guard;

    @BeforeEach
    void setUp() {
        members = mock(FamilyMemberService.class);
        subscriptions = mock(SubscriptionService.class);
        guard = new EntitlementGuard(members, subscriptions);
    }

    private void memberIn(UUID familyId) {
        when(members.getMemberByDeviceToken(TOKEN)).thenReturn(new FamilyMember(
                MEMBER, "n", TOKEN, null, FamilyMember.Role.PARENT, familyId,
                false, OffsetDateTime.now(), OffsetDateTime.now()
        ));
    }

    @Test
    void an_entitled_family_passes() {
        memberIn(FAMILY);
        when(subscriptions.isEntitled(FAMILY)).thenReturn(true);

        assertThatCode(() -> guard.requireEntitled(TOKEN)).doesNotThrowAnyException();
    }

    @Test
    void an_unentitled_family_is_refused() {
        memberIn(FAMILY);
        when(subscriptions.isEntitled(FAMILY)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireEntitled(TOKEN))
                .isInstanceOf(SubscriptionRequiredException.class)
                .hasMessageContaining("Subscription required");
    }

    @Test
    void a_missing_token_is_left_to_the_endpoints_own_authorisation() {
        // The guard must not become a second, weaker auth check -- and it must not be
        // possible to skip one by failing the other.
        assertThatCode(() -> guard.requireEntitled(null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireEntitled("")).doesNotThrowAnyException();
        verify(subscriptions, never()).isEntitled(any());
    }

    @Test
    void a_member_belonging_to_no_family_has_nothing_to_bill() {
        memberIn(null);

        assertThatCode(() -> guard.requireEntitled(TOKEN)).doesNotThrowAnyException();
        verify(subscriptions, never()).isEntitled(any());
    }

}
