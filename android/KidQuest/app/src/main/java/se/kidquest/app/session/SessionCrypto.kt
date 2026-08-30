package se.kidquest.app.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the signed-in session before it touches disk.
 *
 * A device token is a bearer key: whoever holds the string is that family member,
 * with no password to also get past. Stored as plain text it sat in a file readable
 * by anything with filesystem access to the app's data -- and, because auto-backup
 * was on, it was copied into the family's cloud backup as well.
 *
 * The key lives in the Android Keystore, which means it is held by the system (in
 * secure hardware where the device has it) and never leaves the device. So the
 * ciphertext on disk is worthless anywhere else: restore it to another phone and it
 * simply will not open. That is the property that matters here, more than the
 * encryption itself.
 *
 * The key is deliberately NOT tied to the lock screen ([KeyGenParameterSpec.Builder
 * .setUserAuthenticationRequired]). Children open this app on shared phones that often
 * have no lock screen at all, and a key that vanished whenever a parent changed the
 * PIN would sign the whole family out for no gain the threat model asks for.
 */
internal object SessionCrypto {

    private const val TAG = "SessionCrypto"
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "kidquest_session_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** GCM's nominal nonce length; the prefix [decrypt] splits back off. */
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** @return base64 of `iv || ciphertext`, or null if this device cannot encrypt. */
    fun encrypt(plaintext: String): String? = runCatchingCrypto("encrypt") {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    /**
     * @return null when the value cannot be opened -- wrong key, restored from another
     *   device, truncated, tampered with. Every one of those means the same thing to
     *   the caller: there is no session here, sign in again.
     */
    fun decrypt(stored: String): String? = runCatchingCrypto("decrypt") {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) return@runCatchingCrypto null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES),
        )
        String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * Keystore failures are broader than the checked exceptions suggest: OEM
     * implementations throw ProviderException and KeyStoreException at runtime when the
     * secure element is busy or wedged. None of that is worth crashing a family's app
     * over -- the worst honest outcome is one more sign-in.
     */
    private inline fun runCatchingCrypto(what: String, block: () -> String?): String? =
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "session $what failed: ${e.javaClass.simpleName}")
            null
        }
}
