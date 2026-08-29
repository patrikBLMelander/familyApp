package se.kidquest.app.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.prefsDataStore by preferencesDataStore(name = "kidquest_prefs")

private val KEY_ONBOARDING_DISMISSED = booleanPreferencesKey("onboarding_dismissed")
private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")

/**
 * Small local preferences, separate from the session so signing out does not wipe them.
 *
 * The get-started card's dismissal and the dark-mode choice live here. Everything else about that card is
 * derived from real family data on each load, which is what lets it survive a
 * reinstall, appear already complete for a family who signed up on web, and correctly
 * come back if a parent later deletes their only child.
 */
object PrefsStore {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    suspend fun isOnboardingDismissed(): Boolean =
        appContext?.let { it.prefsDataStore.data.first()[KEY_ONBOARDING_DISMISSED] } ?: false

    suspend fun setOnboardingDismissed(dismissed: Boolean) {
        appContext?.prefsDataStore?.edit { it[KEY_ONBOARDING_DISMISSED] = dismissed }
    }

    /**
     * Null until a parent touches the switch, which is what lets the app follow the
     * phone on a fresh install without a three-way control nobody asked for. After
     * that it is their choice and the phone stops having a say.
     */
    suspend fun darkMode(): Boolean? =
        appContext?.let { it.prefsDataStore.data.first()[KEY_DARK_MODE] }

    suspend fun setDarkMode(dark: Boolean) {
        appContext?.prefsDataStore?.edit { it[KEY_DARK_MODE] = dark }
    }
}
