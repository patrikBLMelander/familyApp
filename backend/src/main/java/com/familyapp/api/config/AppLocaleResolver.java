package com.familyapp.api.config;

import com.familyapp.application.familymember.FamilyMemberService;
import com.familyapp.domain.i18n.AppLanguages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Collections;
import java.util.Locale;

/**
 * Which language user-facing text is rendered in, per request:
 * the member's saved language, then Accept-Language, then English.
 *
 * Registered under the bean name DispatcherServlet looks for, so
 * LocaleContextHolder carries this locale into controllers and exception handlers.
 */
@Component("localeResolver")
public class AppLocaleResolver implements LocaleResolver {

    private static final String DEVICE_TOKEN_HEADER = "X-Device-Token";
    private static final String CACHE_ATTRIBUTE = AppLocaleResolver.class.getName() + ".locale";

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

    /** Saved language → first supported Accept-Language → English. */
    static String resolveLanguage(String savedLanguage, HttpServletRequest request) {
        if (AppLanguages.isSupported(savedLanguage)) {
            return savedLanguage;
        }
        if (request.getHeader("Accept-Language") != null) {
            for (var locale : Collections.list(request.getLocales())) {
                if (AppLanguages.isSupported(locale.getLanguage())) {
                    return locale.getLanguage();
                }
            }
        }
        return AppLanguages.FALLBACK;
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
