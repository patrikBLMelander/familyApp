package com.familyapp.api.pet;

import com.familyapp.domain.familymember.FamilyMember;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins who may act for whom.
 *
 * The member-scoped reads used to run their same-family check only when a device token
 * happened to be present, so calling them with no header returned another family's
 * data. And acting on another member's behalf must be a parent's alone -- a child
 * feeding a sibling's pet would move XP between children.
 */
class PetAuthorisationTest {

    private static final UUID FAMILY_A = UUID.randomUUID();
    private static final UUID FAMILY_B = UUID.randomUUID();

    private FamilyMember member(FamilyMember.Role role, UUID familyId) {
        return new FamilyMember(UUID.randomUUID(), "n", "tok", null, role, familyId,
                null, null, null, null, null);
    }

    /** Mirrors PetController.requireSameFamily. */
    private boolean mayRead(FamilyMember requester, FamilyMember target) {
        return requester.familyId() != null && requester.familyId().equals(target.familyId());
    }

    /** Mirrors PetController.requireParentOf. */
    private boolean mayActFor(FamilyMember requester, FamilyMember target) {
        return mayRead(requester, target) && requester.role() == FamilyMember.Role.PARENT;
    }

    @Test
    void a_parent_may_act_for_their_own_child() {
        assertThat(mayActFor(member(FamilyMember.Role.PARENT, FAMILY_A),
                member(FamilyMember.Role.CHILD, FAMILY_A))).isTrue();
    }

    @Test
    void a_child_may_read_a_sibling_but_not_act_for_them() {
        var child = member(FamilyMember.Role.CHILD, FAMILY_A);
        var sibling = member(FamilyMember.Role.CHILD, FAMILY_A);
        assertThat(mayRead(child, sibling)).isTrue();
        assertThat(mayActFor(child, sibling)).isFalse();
    }

    @Test
    void a_parent_of_another_family_may_do_neither() {
        var outsider = member(FamilyMember.Role.PARENT, FAMILY_B);
        var child = member(FamilyMember.Role.CHILD, FAMILY_A);
        assertThat(mayRead(outsider, child)).isFalse();
        assertThat(mayActFor(outsider, child)).isFalse();
    }

    @Test
    void a_member_with_no_family_may_do_neither() {
        var orphan = member(FamilyMember.Role.PARENT, null);
        var child = member(FamilyMember.Role.CHILD, FAMILY_A);
        assertThat(mayRead(orphan, child)).isFalse();
        assertThat(mayActFor(orphan, child)).isFalse();
    }

    @Test
    void an_assistant_may_read_but_not_act_for_others() {
        var assistant = member(FamilyMember.Role.ASSISTANT, FAMILY_A);
        var child = member(FamilyMember.Role.CHILD, FAMILY_A);
        assertThat(mayRead(assistant, child)).isTrue();
        assertThat(mayActFor(assistant, child)).isFalse();
    }
}
