package com.familyapp.api.config;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.familymember.FamilyMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Saved member language, then the app's bare language code, then Swedish for legacy clients. */
class AppLocaleResolverTest {

    private FamilyMemberService members;
    private AppLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        members = mock(FamilyMemberService.class);
        resolver = new AppLocaleResolver(members);
    }

    @Test
    void savedLanguageBeatsTheHeader() {
        when(members.getMemberByDeviceToken("tok")).thenReturn(member("de"));
        var request = request("tok", "es-ES,es;q=0.9");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.forLanguageTag("de"));
    }

    @Test
    void theAppsBareLanguageCodeIsUsedWhenNothingIsSaved() {
        when(members.getMemberByDeviceToken("tok")).thenReturn(member(null));

        assertThat(resolver.resolveLocale(request("tok", "es"))).isEqualTo(Locale.forLanguageTag("es"));
        assertThat(resolver.resolveLocale(request(null, "EN"))).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void legacyClientsGetSwedish() {
        // Android before the translation: no header at all.
        assertThat(resolver.resolveLocale(request(null, null))).isEqualTo(Locale.forLanguageTag("sv"));
        // iOS adds its own header to a Swedish-only app.
        assertThat(resolver.resolveLocale(request(null, "en-US,en;q=0.9"))).isEqualTo(Locale.forLanguageTag("sv"));
        // A browser on the static pages.
        assertThat(resolver.resolveLocale(request(null, "de-DE,de;q=0.9"))).isEqualTo(Locale.forLanguageTag("sv"));
        assertThat(resolver.resolveLocale(request(null, "fr"))).isEqualTo(Locale.forLanguageTag("sv"));
    }

    @Test
    void unknownTokenFallsThroughToTheHeader() {
        when(members.getMemberByDeviceToken("bad")).thenThrow(new IllegalArgumentException("Invalid device token"));

        assertThat(resolver.resolveLocale(request("bad", "de"))).isEqualTo(Locale.forLanguageTag("de"));
    }

    private static MockHttpServletRequest request(String token, String acceptLanguage) {
        var request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("X-Device-Token", token);
        }
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        return request;
    }

    private static FamilyMember member(String language) {
        return new FamilyMember(UUID.randomUUID(), "n", "tok", null, FamilyMember.Role.PARENT,
                UUID.randomUUID(), false, null, null, language);
    }
}
