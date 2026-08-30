package se.kidquest.app.session

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kidquest_session")

/** The whole session, encrypted by [SessionCrypto], as one value. */
private val KEY_SEALED_SESSION = stringPreferencesKey("session_v1")

/**
 * How the session was written before it was encrypted. Read once on start-up so an
 * already signed-in family is carried across the upgrade rather than thrown back to
 * the login screen, then deleted. Removing these is the point of the exercise: keeping
 * the reader forever would keep a plaintext token readable forever.
 */
private val LEGACY_DEVICE_TOKEN = stringPreferencesKey("device_token")
private val LEGACY_MEMBER_ID = stringPreferencesKey("member_id")
private val LEGACY_MEMBER_NAME = stringPreferencesKey("member_name")
private val LEGACY_MEMBER_ROLE = stringPreferencesKey("member_role")
private val LEGACY_FAMILY_ID = stringPreferencesKey("family_id")

private const val TAG = "TokenStore"

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
    val familyId: String?,
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
        val ctx = appContext ?: return
        val prefs = ctx.dataStore.data.first()

        prefs[KEY_SEALED_SESSION]?.let { sealed ->
            val session = SessionCrypto.decrypt(sealed)?.let(::decode)
            if (session == null) {
                // The key that could open this is gone: the value was restored onto a
                // different phone, or the keystore was reset. It will never open, so
                // stop carrying it around.
                Log.w(TAG, "stored session could not be opened; signing out")
                ctx.dataStore.edit { it.remove(KEY_SEALED_SESSION) }
            }
            current = session
            return
        }

        current = migrateLegacy(ctx, prefs)
    }

    fun getSession(): Session? = current

    fun getToken(): String? = current?.deviceToken

    suspend fun setSession(
        deviceToken: String,
        memberId: String?,
        memberName: String?,
        role: String?,
        familyId: String? = null,
    ) {
        val session = Session(deviceToken, memberId, memberName, role, familyId)
        appContext?.let { persist(it, session) }
        current = session
    }

    /** Kept for the token-only call sites; drops any stale identity with it. */
    suspend fun setToken(token: String) = setSession(token, null, null, null, null)

    suspend fun clearToken() {
        appContext?.dataStore?.edit { prefs ->
            prefs.remove(KEY_SEALED_SESSION)
            prefs.removeLegacy()
        }
        current = null
    }

    /**
     * Writes a session the way the app wrote it before encryption, so the migration in
     * [load] can be tested against a real DataStore file rather than reasoned about.
     * Nothing in the app calls this; it exists because getting the migration wrong
     * signs every family out, and that is not a thing to find out from Play reviews.
     */
    @VisibleForTesting
    internal suspend fun writeLegacyPlaintextSession(session: Session) {
        val ctx = appContext ?: return
        ctx.dataStore.edit { prefs ->
            prefs.remove(KEY_SEALED_SESSION)
            prefs[LEGACY_DEVICE_TOKEN] = session.deviceToken
            session.memberId?.let { prefs[LEGACY_MEMBER_ID] = it }
            session.memberName?.let { prefs[LEGACY_MEMBER_NAME] = it }
            session.role?.let { prefs[LEGACY_MEMBER_ROLE] = it }
            session.familyId?.let { prefs[LEGACY_FAMILY_ID] = it }
        }
        current = null
    }

    private suspend fun persist(ctx: Context, session: Session) {
        val sealed = SessionCrypto.encrypt(encode(session))
        ctx.dataStore.edit { prefs ->
            if (sealed != null) {
                prefs[KEY_SEALED_SESSION] = sealed
            } else {
                // Nothing to fall back to: writing it in the clear is the problem this
                // exists to solve. The session still works for as long as the process
                // lives; the next launch asks for a sign-in.
                Log.w(TAG, "session not persisted; device cannot encrypt")
                prefs.remove(KEY_SEALED_SESSION)
            }
            prefs.removeLegacy()
        }
    }

    /**
     * Re-writes a pre-encryption session in sealed form and deletes the plaintext.
     *
     * The delete is unconditional, including when encryption failed. Leaving a readable
     * bearer token behind is worse than one extra sign-in on a device whose keystore is
     * broken -- and it would leave the plaintext there for good, since after the first
     * successful launch this code never runs again.
     */
    private suspend fun migrateLegacy(ctx: Context, prefs: Preferences): Session? {
        val token = prefs[LEGACY_DEVICE_TOKEN]
        if (token == null) {
            if (prefs.hasLegacyRemnants()) {
                ctx.dataStore.edit { it.removeLegacy() }
            }
            return null
        }
        val session = Session(
            deviceToken = token,
            memberId = prefs[LEGACY_MEMBER_ID],
            memberName = prefs[LEGACY_MEMBER_NAME],
            role = prefs[LEGACY_MEMBER_ROLE],
            familyId = prefs[LEGACY_FAMILY_ID],
        )
        persist(ctx, session)
        return session
    }

    private fun MutablePreferences.removeLegacy() {
        remove(LEGACY_DEVICE_TOKEN)
        remove(LEGACY_MEMBER_ID)
        remove(LEGACY_MEMBER_NAME)
        remove(LEGACY_MEMBER_ROLE)
        remove(LEGACY_FAMILY_ID)
    }

    private fun Preferences.hasLegacyRemnants(): Boolean =
        this[LEGACY_MEMBER_ID] != null ||
            this[LEGACY_MEMBER_NAME] != null ||
            this[LEGACY_MEMBER_ROLE] != null ||
            this[LEGACY_FAMILY_ID] != null

    private fun encode(session: Session): String = JSONObject().apply {
        put("deviceToken", session.deviceToken)
        session.memberId?.let { put("memberId", it) }
        session.memberName?.let { put("memberName", it) }
        session.role?.let { put("role", it) }
        session.familyId?.let { put("familyId", it) }
    }.toString()

    private fun decode(json: String): Session? = try {
        val obj = JSONObject(json)
        val token = obj.optString("deviceToken").takeIf { it.isNotBlank() }
        token?.let {
            Session(
                deviceToken = it,
                memberId = obj.optString("memberId").takeIf { v -> v.isNotBlank() },
                memberName = obj.optString("memberName").takeIf { v -> v.isNotBlank() },
                role = obj.optString("role").takeIf { v -> v.isNotBlank() },
                familyId = obj.optString("familyId").takeIf { v -> v.isNotBlank() },
            )
        }
    } catch (e: org.json.JSONException) {
        Log.w(TAG, "stored session was not readable: ${e.javaClass.simpleName}")
        null
    }
}
