package com.familyapp.api.config;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.i18n.AppLanguages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Which language user-facing text is rendered in, per request: the member's saved
 * language, then the app's own language choice, then Swedish.
 *
 * "The app's own choice" is an Accept-Language that is exactly one bare code --
 * "sv", "en", "de" or "es". That is what the translated apps and the web send. Anything
 * else comes from a client that never chose a language and is Swedish-only: the
 * Android app before the translation sends no header at all, and iOS adds one of its
 * own ("en-US,en;q=0.9") to a Swedish app. Falling back to English there turned Swedish
 * errors English in apps already in the stores.
 *
 * Registered under the bean name DispatcherServlet looks for, so
 * LocaleContextHolder carries this locale into controllers and exception handlers.
 */
@Component("localeResolver")
public class AppLocaleResolver implements LocaleResolver {

    private static final String DEVICE_TOKEN_HEADER = "X-Device-Token";
    private static final String CACHE_ATTRIBUTE = AppLocaleResolver.class.getName() + ".locale";
    /** Clients that never chose a language are the Swedish-only ones. */
    private static final String LEGACY_CLIENT_LANGUAGE = "sv";

    private final FamilyMemberService memberService;

    public AppLocaleResolver(FamilyMemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        if (request.getAttribute(CACHE_ATTRIBUTE) instanceof Locale cached) {
            return cached;
        }
        var locale = Locale.forLanguageTag(resolveLanguage(savedLanguage(request), request));
        request.setAttribute(CACHE_ATTRIBUTE, locale);
        return locale;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("The locale follows the member and Accept-Language");
    }

    /** Saved language → an app-chosen Accept-Language → Swedish. */
    static String resolveLanguage(String savedLanguage, HttpServletRequest request) {
        if (AppLanguages.isSupported(savedLanguage)) {
            return savedLanguage;
        }
        var header = request.getHeader("Accept-Language");
        var chosen = header == null ? null : header.trim().toLowerCase(Locale.ROOT);
        if (AppLanguages.isSupported(chosen)) {
            return chosen;
        }
        return LEGACY_CLIENT_LANGUAGE;
    }

    private String savedLanguage(HttpServletRequest request) {
        var token = request.getHeader(DEVICE_TOKEN_HEADER);
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return memberService.getMemberByDeviceToken(token).language();
        } catch (RuntimeException e) {
            // Unknown or stale token: the endpoint itself will reject it; just fall through.
            return null;
        }
    }
}
