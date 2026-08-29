package com.familyapp.application.passwordreset;

import com.familyapp.infrastructure.email.ResendEmailSender;
import com.familyapp.infrastructure.familymember.FamilyMemberEntity;
import com.familyapp.infrastructure.familymember.FamilyMemberJpaRepository;
import com.familyapp.infrastructure.passwordreset.PasswordResetTokenEntity;
import com.familyapp.infrastructure.passwordreset.PasswordResetTokenJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A reset token is a temporary password. These tests pin the properties that make it
 * safe to send one by e-mail, and the ones that stop this endpoint from becoming a way
 * to find out which families use the app.
 */
class PasswordResetServiceTest {

    private static final UUID MEMBER = UUID.randomUUID();
    private static final String EMAIL = "parent@example.com";

    private FamilyMemberJpaRepository members;
    private PasswordResetTokenJpaRepository tokens;
    private ResendEmailSender email;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        members = mock(FamilyMemberJpaRepository.class);
        tokens = mock(PasswordResetTokenJpaRepository.class);
        email = mock(ResendEmailSender.class);
        when(tokens.save(any())).thenAnswer(i -> i.getArgument(0));
        when(members.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new PasswordResetService(
                members, tokens, new BCryptPasswordEncoder(), email, "https://example.test/reset"
        );
    }

    private FamilyMemberEntity member(String role) {
        var e = new FamilyMemberEntity();
        e.setId(MEMBER);
        e.setName("Patrik");
        e.setEmail(EMAIL);
        e.setRole(role);
        e.setPasswordHash("old-hash");
        e.setCreatedAt(OffsetDateTime.now());
        e.setUpdatedAt(OffsetDateTime.now());
        when(members.findByEmail(EMAIL)).thenReturn(Optional.of(e));
        when(members.findById(MEMBER)).thenReturn(Optional.of(e));
        return e;
    }

    private PasswordResetTokenEntity savedToken() {
        var captor = ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(tokens).save(captor.capture());
        return captor.getValue();
    }

    private String emailedLink() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq(EMAIL), anyString(), captor.capture());
        var body = captor.getValue();
        var start = body.indexOf("https://example.test/reset?token=");
        return body.substring(start, body.indexOf('"', start));
    }

    @Test
    void an_unknown_address_is_answered_the_same_way_and_leaves_no_trace() {
        when(members.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service.request("nobody@example.com")).doesNotThrowAnyException();

        verify(tokens, never()).save(any());
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void a_child_has_no_password_to_reset() {
        member("CHILD");

        service.request(EMAIL);

        verify(tokens, never()).save(any());
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void the_token_is_stored_hashed_and_never_in_the_clear() {
        member("PARENT");

        service.request(EMAIL);

        var raw = emailedLink().substring("https://example.test/reset?token=".length());
        var stored = savedToken().getTokenHash();

        assertThat(stored).isNotEqualTo(raw);
        assertThat(stored).hasSize(64);
        // The whole point: what is in the database cannot be pasted into the link.
        assertThat(stored).doesNotContain(raw);
    }

    @Test
    void requesting_again_too_soon_is_ignored() {
        member("PARENT");
        var recent = new PasswordResetTokenEntity();
        recent.setCreatedAt(OffsetDateTime.now().minusSeconds(30));
        when(tokens.findFirstByMemberIdOrderByCreatedAtDesc(MEMBER)).thenReturn(Optional.of(recent));

        service.request(EMAIL);

        verify(tokens, never()).save(any());
        verify(email, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void a_new_request_burns_any_outstanding_link() {
        member("PARENT");

        service.request(EMAIL);

        verify(tokens).invalidateOutstanding(eq(MEMBER), any());
    }

    @Test
    void a_valid_link_sets_the_password_and_spends_the_token() {
        var entity = member("PARENT");
        service.request(EMAIL);
        var raw = emailedLink().substring("https://example.test/reset?token=".length());
        var token = savedToken();
        when(tokens.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

        service.confirm(raw, "nyttlosen");

        assertThat(entity.getPasswordHash()).isNotEqualTo("old-hash");
        assertThat(new BCryptPasswordEncoder().matches("nyttlosen", entity.getPasswordHash())).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void a_link_cannot_be_used_twice() {
        member("PARENT");
        var token = new PasswordResetTokenEntity();
        token.setMemberId(MEMBER);
        token.setTokenHash("whatever");
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        token.setUsedAt(OffsetDateTime.now().minusMinutes(1));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirm("anything", "nyttlosen"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void an_expired_link_is_refused() {
        member("PARENT");
        var token = new PasswordResetTokenEntity();
        token.setMemberId(MEMBER);
        token.setTokenHash("whatever");
        token.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.confirm("anything", "nyttlosen"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void expired_used_and_unknown_all_fail_identically() {
        // Same message for every failure, so nobody can tell a real token from a
        // fictional one by reading the error.
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm("made-up", "nyttlosen"))
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void a_too_short_password_is_refused_before_the_token_is_spent() {
        assertThatThrownBy(() -> service.confirm("anything", "kort"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 6");
        verify(tokens, never()).findByTokenHash(anyString());
    }
}
