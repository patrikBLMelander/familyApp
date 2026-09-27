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

/** Saved member language, then Accept-Language, then English. */
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
    void acceptLanguageIsUsedWhenNothingIsSaved() {
        when(members.getMemberByDeviceToken("tok")).thenReturn(member(null));

        assertThat(resolver.resolveLocale(request("tok", "sv-SE,sv;q=0.9,en;q=0.8")))
                .isEqualTo(Locale.forLanguageTag("sv"));
    }

    @Test
    void firstSupportedAcceptLanguageWins() {
        assertThat(resolver.resolveLocale(request(null, "fr-FR,fr;q=0.9,es;q=0.5")))
                .isEqualTo(Locale.forLanguageTag("es"));
    }

    @Test
    void englishWhenNothingMatches() {
        assertThat(resolver.resolveLocale(request(null, "fr-FR"))).isEqualTo(Locale.ENGLISH);
        assertThat(resolver.resolveLocale(request(null, null))).isEqualTo(Locale.ENGLISH);
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
