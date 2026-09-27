package se.kidquest.app.i18n

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency

/**
 * Formats wallet amounts in the family's currency and the UI's language.
 *
 * Amounts are whole units everywhere in KidQuest, so no decimals are shown. The
 * family currency is remembered from whichever response last carried it (the family,
 * or a wallet balance), so screens can format without threading it through.
 */
object Money {

    val SUPPORTED = listOf("SEK", "EUR", "USD", "GBP", "NOK", "DKK", "CHF")
    const val DEFAULT = "SEK"
    private val KRONOR = setOf("SEK", "NOK", "DKK")

    @Volatile
    var familyCurrency: String = DEFAULT
        private set

    /** Remembers the family's currency; null or unknown codes are ignored. */
    fun remember(currency: String?) {
        if (currency != null && currency in SUPPORTED) {
            familyCurrency = currency
        }
    }

    fun format(amount: Int, currency: String = familyCurrency): String {
        val format = NumberFormat.getCurrencyInstance(L10n.locale())
        runCatching { format.currency = Currency.getInstance(currency) }
        if (currency in KRONOR && format is DecimalFormat) {
            // Kronor read "kr" in every language, as on iOS; "SEK 120" feels wrong in a kid's wallet.
            format.decimalFormatSymbols = format.decimalFormatSymbols.apply { currencySymbol = "kr" }
        }
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    /** The currency's symbol alone, for input-field suffixes ("kr", "€"). */
    fun symbol(currency: String = familyCurrency): String =
        if (currency in KRONOR) "kr" else runCatching { Currency.getInstance(currency).getSymbol(L10n.locale()) }.getOrDefault(currency)
}
