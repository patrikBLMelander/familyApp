package se.kidquest.app.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kidquest_session")

private val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")

object TokenStore {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentToken: String? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    suspend fun load() {
        appContext?.let { ctx ->
            currentToken = ctx.dataStore.data.first()[KEY_DEVICE_TOKEN]
        }
    }

    fun getToken(): String? = currentToken

    suspend fun setToken(token: String) {
        appContext?.let { ctx ->
            ctx.dataStore.edit { prefs ->
                prefs[KEY_DEVICE_TOKEN] = token
            }
        }
        currentToken = token
    }

    suspend fun clearToken() {
        appContext?.let { ctx ->
            ctx.dataStore.edit { prefs ->
                prefs.remove(KEY_DEVICE_TOKEN)
            }
        }
        currentToken = null
    }
}
