package com.familyapp.application.i18n;

import com.familyapp.domain.i18n.LocalizedException;
import com.familyapp.domain.i18n.Money;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

/**
 * Renders message codes in the request's language (see AppLocaleResolver), falling
 * back to English. Money arguments are formatted as currency for that language.
 */
@Component
public class MessageTranslator {

    private final MessageSource messageSource;

    public MessageTranslator(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** The code in the current request's language. */
    public String get(String code, Object... args) {
        return get(LocaleContextHolder.getLocale(), code, args);
    }

    /** The code in an explicit language. */
    public String get(Locale locale, String code, Object... args) {
        var formatted = Arrays.stream(args).map(arg -> formatArg(arg, locale)).toArray();
        return messageSource.getMessage(code, formatted, code, locale);
    }

    /** A LocalizedException's text in the current request's language. */
    public String get(LocalizedException ex) {
        return get(ex.code(), ex.args());
    }

    /** The code's text if one exists for it, otherwise null -- for optional lookups. */
    public String find(String code) {
        return messageSource.getMessage(code, null, null, LocaleContextHolder.getLocale());
    }

    /**
     * Text the system itself wrote into the database in Swedish (transaction
     * descriptions, default expense categories) -> message code. Matched on read, so
     * rows written before i18n translate too. User-typed text is never in here.
     */
    private static final Map<String, String> SYSTEM_TEXT = Map.of(
            "Veckopeng", "system.transaction.weeklyAllowance",
            "Månadspeng", "system.transaction.monthlyAllowance",
            "Månadspeng efter nivå", "system.transaction.levelAllowance",
            "Fördelning till sparmål", "system.transaction.goalAllocation",
            "Godis", "system.category.sweets",
            "Leksaker", "system.category.toys",
            "Kläder", "system.category.clothes"
    );

    /** A system-written stored text in the request's language; anything else unchanged. */
    public String systemText(String stored) {
        if (stored == null) {
            return null;
        }
        var code = SYSTEM_TEXT.get(stored.trim());
        return code == null ? stored : get(code);
    }

    /** Whole-unit currency amount for a locale, e.g. "120 kr" or "120 €". */
    public static String formatMoney(Money money, Locale locale) {
        var format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance(money.currency()));
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        return format.format(money.amount());
    }

    private static Object formatArg(Object arg, Locale locale) {
        if (arg instanceof Money money) {
            return formatMoney(money, locale);
        }
        // Keep numbers as plain text so MessageFormat doesn't add grouping to years/ids.
        if (arg instanceof Number number) {
            return number.toString();
        }
        return arg;
    }
}
