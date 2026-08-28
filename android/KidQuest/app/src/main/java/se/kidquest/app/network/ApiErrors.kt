package se.kidquest.app.network

import android.util.Log
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
     * Backend messages are English and aimed at developers. These are the ones a
     * user can actually run into, phrased so the next step is obvious.
     */
    private val translations = mapOf(
        "No account found with this email" to
            "Det finns inget konto med den e-postadressen. Har du skapat ett konto än?",
        "Invalid password" to
            "Fel lösenord. Försök igen.",
        "Email is required" to
            "Fyll i din e-postadress.",
        "Password is required" to
            "Fyll i ditt lösenord.",
        "Password not set for this account. Please set a password first." to
            "Kontot har inget lösenord än. Sätt ett lösenord i webbappen först.",
        "Email login is only available for parent or assistant users" to
            "Bara föräldrar loggar in med e-post. Barn använder QR-koden eller en kod.",
        "Password must be at least 6 characters long" to
            "Lösenordet måste vara minst 6 tecken.",
        "Invalid device token" to
            "Inloggningen gäller inte längre. Be en förälder visa en ny kod.",
        "Family member not found for device token" to
            "Inloggningen gäller inte längre. Be en förälder visa en ny kod.",
        "Invalid invite token" to
            "Koden stämmer inte. Be en förälder visa en ny kod.",
        "Invite token has expired" to
            "Koden har gått ut. Be en förälder visa en ny kod.",
        "Device token already in use" to
            "Den här telefonen är redan kopplad till någon annan i familjen.",
        "Pet already selected for this month" to
            "Ett djur är redan valt den här månaden.",
        "No unfed food available" to
            "Det finns ingen mat att ge just nu.",
        "Subscription required for this action" to
            "Provperioden har gått ut. Barnens sysslor och djur fungerar som vanligt, " +
                "men för att lägga till eller ändra behöver familjen en prenumeration.",
    )

    /**
     * @param fallback shown when the failure carries nothing useful, so each caller
     *   can stay specific about what it was trying to do.
     */
    fun message(throwable: Throwable, fallback: String): String {
        // No connection at all is worth saying out loud rather than blaming the input.
        if (throwable is IOException) {
            return "Kunde inte nå servern. Kontrollera din uppkoppling."
        }

        if (throwable is HttpException) {
            serverMessage(throwable)?.let { server ->
                return translations[server] ?: server
            }
            // Nothing parseable in the body: at least distinguish "your fault" from
            // "our fault", which a bare status code does not.
            return when (throwable.code()) {
                401, 403 -> "Du har inte behörighet till det här."
                // The server refused for non-payment rather than for permissions. The
                // dashboard's banner is already on screen and leads to the paywall, so
                // this only has to explain, not navigate.
                402 -> "Provperioden har gått ut. Förnya för att lägga till eller ändra."
                404 -> fallback
                in 500..599 -> "Något gick fel hos servern. Försök igen om en stund."
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
