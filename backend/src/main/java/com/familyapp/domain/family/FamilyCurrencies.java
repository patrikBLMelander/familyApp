package com.familyapp.domain.family;

import java.util.Set;

/** Currencies a family can keep its kids' wallets in (ISO 4217). */
public final class FamilyCurrencies {

    public static final Set<String> SUPPORTED = Set.of("SEK", "EUR", "USD", "GBP", "NOK", "DKK", "CHF");
    public static final String DEFAULT = "SEK";

    private FamilyCurrencies() {
    }

    /** True for one of {@link #SUPPORTED}. */
    public static boolean isSupported(String currency) {
        return currency != null && SUPPORTED.contains(currency);
    }

    /** The family's currency, or the default for a family row that predates the column. */
    public static String orDefault(String currency) {
        return isSupported(currency) ? currency : DEFAULT;
    }
}
