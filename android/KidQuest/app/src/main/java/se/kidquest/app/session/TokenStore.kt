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
private val KEY_MEMBER_ID = stringPreferencesKey("member_id")
private val KEY_MEMBER_NAME = stringPreferencesKey("member_name")
private val KEY_MEMBER_ROLE = stringPreferencesKey("member_role")

/**
 * Who is signed in on this device.
 *
 * The role is stored alongside the token because start-up has to route on it: a
 * device token alone does not say whether it belongs to a parent or a child, and
 * sending a child to the adult dashboard hands them task management and the family
 * wallet. Keeping it local also means start-up needs no network round trip.
 */
data class Session(
    val deviceToken: String,
    val memberId: String?,
    val memberName: String?,
    val role: String?,
) {
    val isChild: Boolean get() = role?.uppercase() == "CHILD"

    /** True for sessions written before the role was persisted; needs a lookup. */
    val isIncomplete: Boolean get() = role.isNullOrBlank() || memberId.isNullOrBlank()
}

object TokenStore {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var current: Session? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    suspend fun load() {
        appContext?.let { ctx ->
            val prefs = ctx.dataStore.data.first()
            val token = prefs[KEY_DEVICE_TOKEN]
            current = token?.let {
                Session(
                    deviceToken = it,
                    memberId = prefs[KEY_MEMBER_ID],
                    memberName = prefs[KEY_MEMBER_NAME],
                    role = prefs[KEY_MEMBER_ROLE],
                )
            }
        }
    }

    fun getSession(): Session? = current

    fun getToken(): String? = current?.deviceToken

    suspend fun setSession(
        deviceToken: String,
        memberId: String?,
        memberName: String?,
        role: String?,
    ) {
        appContext?.let { ctx ->
            ctx.dataStore.edit { prefs ->
                prefs[KEY_DEVICE_TOKEN] = deviceToken
                if (memberId != null) prefs[KEY_MEMBER_ID] = memberId else prefs.remove(KEY_MEMBER_ID)
                if (memberName != null) prefs[KEY_MEMBER_NAME] = memberName else prefs.remove(KEY_MEMBER_NAME)
                if (role != null) prefs[KEY_MEMBER_ROLE] = role else prefs.remove(KEY_MEMBER_ROLE)
            }
        }
        current = Session(deviceToken, memberId, memberName, role)
    }

    /** Kept for the token-only call sites; drops any stale identity with it. */
    suspend fun setToken(token: String) = setSession(token, null, null, null)

    suspend fun clearToken() {
        appContext?.let { ctx ->
            ctx.dataStore.edit { prefs ->
                prefs.remove(KEY_DEVICE_TOKEN)
                prefs.remove(KEY_MEMBER_ID)
                prefs.remove(KEY_MEMBER_NAME)
                prefs.remove(KEY_MEMBER_ROLE)
            }
        }
        current = null
    }
}
