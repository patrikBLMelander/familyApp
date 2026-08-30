package se.kidquest.app.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs on a device because the Android Keystore only exists on one: the point of these
 * tests is the real key, not a stand-in for it.
 *
 * Two things are being defended. That a family already signed in stays signed in
 * across the upgrade -- getting that wrong logs out every installed device at once,
 * and children cannot simply log back in, they need a parent with a QR code. And that
 * once migrated, the token is genuinely no longer readable on disk, which is the whole
 * reason for the change.
 */
@RunWith(AndroidJUnit4::class)
class SessionStorageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val sessionFile: File
        get() = File(context.filesDir, "datastore/kidquest_session.preferences_pb")

    /** ISO-8859-1 maps every byte to a char, so a substring search cannot miss one. */
    private fun rawStoredBytes(): String =
        if (sessionFile.exists()) sessionFile.readBytes().toString(Charsets.ISO_8859_1) else ""

    @Before
    fun signOutFirst() = runBlocking {
        TokenStore.init(context)
        TokenStore.clearToken()
    }

    @Test
    fun encryptedValueRoundTrips() {
        val sealed = SessionCrypto.encrypt("device-token-round-trip")
        assertTrue("device could not encrypt at all", sealed != null)
        assertFalse("ciphertext still contains the plaintext", sealed!!.contains("round-trip"))
        assertEquals("device-token-round-trip", SessionCrypto.decrypt(sealed))
    }

    @Test
    fun tamperedOrForeignValueDoesNotOpen() {
        val sealed = SessionCrypto.encrypt("device-token-tamper")!!
        // Flipping one character is what a value restored from another phone looks
        // like from here: authentic-looking, unopenable.
        val flipped = sealed.replaceRange(4, 5, if (sealed[4] == 'A') "B" else "A")
        assertNull(SessionCrypto.decrypt(flipped))
        assertNull(SessionCrypto.decrypt("not-even-base64-%%%"))
        assertNull(SessionCrypto.decrypt(""))
    }

    @Test
    fun legacyPlaintextSessionIsCarriedOverAndErased() = runBlocking {
        val legacy = Session(
            deviceToken = "legacy-token-abc123",
            memberId = "member-77",
            memberName = "Testbarnet",
            role = "CHILD",
            familyId = "family-9",
        )
        TokenStore.writeLegacyPlaintextSession(legacy)
        assertTrue(
            "test is meaningless unless the old format really was readable",
            rawStoredBytes().contains("legacy-token-abc123"),
        )

        TokenStore.load()

        val migrated = TokenStore.getSession()
        assertEquals("the signed-in family was thrown out by the migration", legacy, migrated)
        assertTrue(migrated!!.isChild)

        val onDisk = rawStoredBytes()
        assertFalse("token is still readable on disk", onDisk.contains("legacy-token-abc123"))
        assertFalse("child's name is still readable on disk", onDisk.contains("Testbarnet"))
        assertFalse("legacy key was left behind", onDisk.contains("device_token"))
    }

    @Test
    fun newSessionSurvivesARestartAndIsNotReadable() = runBlocking {
        TokenStore.setSession(
            deviceToken = "fresh-token-xyz789",
            memberId = "member-1",
            memberName = "Foralder",
            role = "PARENT",
            familyId = "family-1",
        )
        assertFalse(rawStoredBytes().contains("fresh-token-xyz789"))

        // What start-up does on the next launch.
        TokenStore.load()

        assertEquals("fresh-token-xyz789", TokenStore.getToken())
        assertEquals("PARENT", TokenStore.getSession()?.role)
        assertFalse(TokenStore.getSession()!!.isChild)
    }

    @Test
    fun signingOutLeavesNothingBehind() = runBlocking {
        TokenStore.setSession("token-to-forget", "m", "n", "PARENT", "f")
        TokenStore.clearToken()

        assertNull(TokenStore.getSession())
        TokenStore.load()
        assertNull("sign-out did not reach the disk", TokenStore.getSession())
    }
}
