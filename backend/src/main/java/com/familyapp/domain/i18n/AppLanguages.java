package com.familyapp.domain.i18n;

import java.util.Set;

/** The languages the apps ship in. Anything else falls back to English. */
public final class AppLanguages {

    public static final Set<String> SUPPORTED = Set.of("sv", "en", "de", "es");
    public static final String FALLBACK = "en";

    private AppLanguages() {
    }

    /** True for sv/en/de/es. */
    public static boolean isSupported(String language) {
        return language != null && SUPPORTED.contains(language);
    }
}
