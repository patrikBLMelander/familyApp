package se.kidquest.app.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.prefsDataStore by preferencesDataStore(name = "kidquest_prefs")

private val KEY_ONBOARDING_DISMISSED = booleanPreferencesKey("onboarding_dismissed")

/**
 * Small local preferences, separate from the session so signing out does not wipe them.
 *
 * Only the get-started card's dismissal lives here. Everything else about that card is
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
}
