package se.kidquest.app.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The app's language, readable from anywhere -- view models, network code, helpers.
 *
 * Compose screens get the right strings through their activity, which AppCompat wraps
 * in the chosen locale. The application context is not wrapped on API < 33, so code
 * without an activity at hand asks here: [str] resolves against a context configured
 * for the in-app choice, or the phone's own languages when there is none.
 */
object L10n {

    /** Languages the app ships. Anything else resolves to English. */
    val SUPPORTED = listOf("sv", "en", "de", "es")
    const val FALLBACK = "en"

    @Volatile
    private var app: Context? = null

    @Volatile
    private var cached: Pair<String, Context>? = null

    fun init(context: Context) {
        if (app == null) {
            app = context.applicationContext
        }
    }

    /** The in-app choice, or null when the app follows the phone. */
    fun chosenLanguage(): String? {
        val chosen = AppCompatDelegate.getApplicationLocales()
        return if (chosen.isEmpty) null else chosen[0]?.language
    }

    /** sv / en / de / es: the language the UI is actually shown in. */
    fun language(): String {
        chosenLanguage()?.let { if (it in SUPPORTED) return it }
        val system = Resources.getSystem().configuration.locales
        for (i in 0 until system.size()) {
            val lang = system[i].language
            if (lang in SUPPORTED) return lang
        }
        return FALLBACK
    }

    /**
     * Locale for formatting numbers, money and dates in the UI language. Keeps the
     * region when the chosen or phone locale speaks that language, so an American phone
     * in English gets "$85" rather than a British "US$85".
     */
    fun locale(): Locale {
        val lang = language()
        val chosen = AppCompatDelegate.getApplicationLocales()
        val candidates = (0 until chosen.size()).mapNotNull { chosen[it] } +
            Resources.getSystem().configuration.locales.let { list -> (0 until list.size()).map { list[it] } }
        candidates.firstOrNull { it.language == lang && it.country.isNotEmpty() }?.let { return it }
        return defaultLocale(lang)
    }

    private fun defaultLocale(language: String): Locale = when (language) {
        "sv" -> Locale("sv", "SE")
        "de" -> Locale.GERMANY
        "es" -> Locale("es", "ES")
        else -> Locale.UK
    }

    private fun context(): Context? {
        val base = app ?: return null
        val chosen = AppCompatDelegate.getApplicationLocales()
        val tag = chosen.toLanguageTags()
        cached?.let { (t, c) -> if (t == tag) return c }
        val config = Configuration(base.resources.configuration)
        if (chosen.isEmpty) {
            config.setLocales(Resources.getSystem().configuration.locales)
        } else {
            config.setLocales(LocaleList.forLanguageTags(tag))
        }
        val localized = base.createConfigurationContext(config)
        cached = tag to localized
        return localized
    }

    fun str(@StringRes id: Int, vararg args: Any): String {
        val c = context() ?: return ""
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val c = context() ?: return ""
        val formatArgs = if (args.isEmpty()) arrayOf<Any>(count) else args
        return c.resources.getQuantityString(id, count, *formatArgs)
    }

    /** Switches the UI language; null follows the phone. Activities are recreated. */
    fun apply(language: String?) {
        val list = if (language == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }
        AppCompatDelegate.setApplicationLocales(list)
        cached = null
    }
}

/** Shorthand for [L10n.str], usable anywhere, composable or not. */
fun tr(@StringRes id: Int, vararg args: Any): String = L10n.str(id, *args)

/** Shorthand for [L10n.plural]; the count is the format argument unless others are given. */
fun trp(@PluralsRes id: Int, count: Int, vararg args: Any): String = L10n.plural(id, count, *args)
