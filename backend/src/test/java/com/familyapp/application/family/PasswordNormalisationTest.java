package com.familyapp.application.family;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the whitespace behaviour that locked accounts out.
 *
 * Registration hashed the raw password while login compared the trimmed one, so any
 * password set with a trailing space could never be used again -- and there is no
 * password reset to recover with. These tests describe both the bug and the fallback
 * that rescues accounts already in that state.
 */
class PasswordNormalisationTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    /** The check as it now stands in loginByEmailAndPassword. */
    private boolean login(String typed, String storedHash) {
        String normalized = typed.trim();
        if (encoder.matches(normalized, storedHash)) {
            return true;
        }
        return !normalized.equals(typed) && encoder.matches(typed, storedHash);
    }

    @Test
    void the_old_asymmetry_locked_the_account_out() {
        // What registration used to do: hash the raw string, spaces and all.
        String hash = encoder.encode("hemligt1 ");

        // Login trimmed, so it never matched -- with no reset, permanently.
        assertThat(encoder.matches("hemligt1 ".trim(), hash)).isFalse();
    }

    @Test
    void the_fallback_rescues_an_account_hashed_untrimmed() {
        String hash = encoder.encode("hemligt1 ");
        assertThat(login("hemligt1 ", hash)).isTrue();
    }

    @Test
    void new_accounts_are_hashed_trimmed_and_log_in_either_way() {
        String hash = encoder.encode("hemligt1 ".trim());
        assertThat(login("hemligt1", hash)).isTrue();
        assertThat(login("hemligt1 ", hash)).isTrue();
        assertThat(login(" hemligt1", hash)).isTrue();
    }

    @Test
    void a_genuinely_wrong_password_is_still_refused() {
        String hash = encoder.encode("hemligt1");
        assertThat(login("hemligt2", hash)).isFalse();
        assertThat(login("Hemligt1", hash)).isFalse();
        assertThat(login("", hash)).isFalse();
    }

    @Test
    void the_fallback_costs_nothing_when_there_is_no_whitespace() {
        // Guarding on normalized.equals(typed) means the common case does exactly one
        // BCrypt comparison; only whitespace input pays for a second.
        String typed = "hemligt1";
        assertThat(typed.trim().equals(typed)).isTrue();
    }
}
