package com.familyapp.application.i18n;

import com.familyapp.domain.i18n.LocalizedException;
import com.familyapp.domain.i18n.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

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

    @Test
    void everyLanguageHasTheSameMessages() throws IOException {
        // A key missing in one file would fall back to English for that language only --
        // easy to miss by hand, so every file has to list exactly what the English one does.
        var english = load("messages.properties").stringPropertyNames();
        for (var file : new String[] {"messages_sv.properties", "messages_de.properties", "messages_es.properties"}) {
            assertThat(load(file).stringPropertyNames()).as(file).isEqualTo(english);
        }
    }

    private static Properties load(String file) throws IOException {
        var properties = new Properties();
        try (var in = MessageTranslatorTest.class.getClassLoader().getResourceAsStream(file)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void rewardNamesFollowTheLanguageAndUnknownOnesKeepTheirStoredName() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThat(translator.lootName("frame_forest", "Skogsram")).isEqualTo("Waldrahmen");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es"));
        assertThat(translator.lootName("item_lanterns", "Lyktor")).isEqualTo("Farolillos");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("sv"));
        assertThat(translator.lootName("item_kite", "Drake")).isEqualTo("Drake");
        // An item added to the catalog later, before anyone translates it.
        assertThat(translator.lootName("frame_new_one", "Ny ram")).isEqualTo("Ny ram");
    }
}
