package se.kidquest.app.network

import android.util.Log
import se.kidquest.app.R
import se.kidquest.app.i18n.tr
import retrofit2.HttpException
import java.io.IOException
import org.json.JSONObject

/**
 * Turns a failed call into something a parent can act on.
 *
 * Retrofit's HttpException.message is only ever "HTTP 400 Bad Request", so using
 * it directly threw away the reason the backend had already supplied. Testers hit
 * this on the login screen: they saw "Fel: HTTP 400 Bad Request" while the server
 * had said, plainly, that no account existed with that email.
 *
 * The backend answers with {"error": "..."} via GlobalExceptionHandler, so the real
 * message is always there to be read.
 */
object ApiErrors {

    private const val TAG = "ApiErrors"

    /**
     * Backend messages that are English and aimed at developers. These are the ones a
     * user can actually run into, phrased so the next step is obvious. Messages the
     * backend already localizes (it reads Accept-Language) pass through unchanged.
     */
    private val translations = mapOf(
        "No account found with this email" to R.string.err_no_account,
        "Invalid password" to R.string.err_wrong_password,
        "Email is required" to R.string.err_email_required,
        "Password is required" to R.string.err_password_required,
        "Password not set for this account. Please set a password first." to R.string.err_password_not_set,
        "Email login is only available for parent or assistant users" to R.string.err_email_login_adults,
        "Password must be at least 6 characters long" to R.string.err_password_short,
        "Invalid device token" to R.string.err_login_expired,
        "Family member not found for device token" to R.string.err_login_expired,
        "Invalid invite token" to R.string.err_invite_invalid,
        "Invite token has expired" to R.string.err_invite_expired,
        "Device token already in use" to R.string.err_device_in_use,
        "Pet already selected for this month" to R.string.err_pet_already_selected,
        "No unfed food available" to R.string.err_no_unfed_food,
        "Subscription required for this action" to R.string.err_subscription_required,
    )

    /**
     * @param fallback shown when the failure carries nothing useful, so each caller
     *   can stay specific about what it was trying to do.
     */
    fun message(throwable: Throwable, fallback: String): String {
        // No connection at all is worth saying out loud rather than blaming the input.
        if (throwable is IOException) {
            return tr(R.string.err_no_connection)
        }

        if (throwable is HttpException) {
            serverMessage(throwable)?.let { server ->
                return translations[server]?.let { tr(it) } ?: server
            }
            // Nothing parseable in the body: at least distinguish "your fault" from
            // "our fault", which a bare status code does not.
            return when (throwable.code()) {
                401, 403 -> tr(R.string.err_forbidden)
                // The server refused for non-payment rather than for permissions. The
                // dashboard's banner is already on screen and leads to the paywall, so
                // this only has to explain, not navigate.
                402 -> tr(R.string.err_trial_ended)
                404 -> fallback
                in 500..599 -> tr(R.string.err_server)
                else -> fallback
            }
        }

        return throwable.message?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun serverMessage(e: HttpException): String? =
        try {
            e.response()?.errorBody()?.string()
                ?.takeIf { it.isNotBlank() }
                ?.let { JSONObject(it).optString("error").takeIf { m -> m.isNotBlank() } }
        } catch (parseFailure: Exception) {
            // A non-JSON body (a proxy error page, say) is not worth failing over.
            Log.w(TAG, "Could not read error body: ${parseFailure.message}")
            null
        }
}
