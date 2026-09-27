package com.familyapp.application.i18n;

import com.familyapp.domain.i18n.LocalizedException;
import com.familyapp.domain.i18n.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real message bundles: codes render per language, money uses the family's
 * currency, unknown languages fall back to English, and system-written text
 * translates on read while user text is left alone.
 */
class MessageTranslatorTest {

    private MessageTranslator translator;

    @BeforeEach
    void setUp() {
        // Same settings as spring.messages in application.yml.
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        translator = new MessageTranslator(source);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void localizedErrorRendersInGermanWithTheFamilyCurrency() {
        LocaleContextHolder.setLocale(Locale.GERMAN);

        var text = translator.get(new LocalizedException("wallet.insufficientFunds", new Money(120, "EUR")));

        assertThat(text).startsWith("Du hast nicht genug Geld. Du hast 120").contains("€").doesNotContain("kr");
    }

    @Test
    void swedishKeepsKronor() {
        var text = translator.get(Locale.forLanguageTag("sv"), "wallet.insufficientFunds", new Money(45, "SEK"));

        assertThat(text).startsWith("Du har inte tillräckligt med pengar. Du har 45").contains("kr");
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        assertThat(translator.get(Locale.FRENCH, "amount.positive")).isEqualTo("The amount must be greater than 0");
    }

    @Test
    void goalNameWithQuotesSurvivesFormatting() {
        var text = translator.get(Locale.ENGLISH, "wallet.goal.wouldOverflow", "Lego", new Money(30, "USD"));

        assertThat(text).isEqualTo("That is more than \"Lego\" needs. At most $30 left.");
    }

    @Test
    void systemWrittenTransactionTextTranslatesOnRead() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es"));

        assertThat(translator.systemText("Månadspeng")).isEqualTo("Paga mensual");
        assertThat(translator.systemText("Godis")).isEqualTo("Dulces");
        // Something a parent typed stays exactly as typed.
        assertThat(translator.systemText("Glass på stranden")).isEqualTo("Glass på stranden");
    }
}
