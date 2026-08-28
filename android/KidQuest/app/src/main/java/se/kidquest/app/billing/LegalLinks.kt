package se.kidquest.app.billing

/**
 * The two documents Google requires a subscription app to link to.
 *
 * Both live in the web app rather than being duplicated per platform, so there is one
 * version of the text to keep current -- and so a correction does not need a new
 * release on two stores.
 *
 * The privacy policy already exists and is linked from the web login screen. The
 * terms page is new; a subscription cannot ship without it.
 */
object LegalLinks {

    private const val WEB_BASE = "https://familyapp-frontend-production.up.railway.app"

    const val PRIVACY = "$WEB_BASE/privacy"
    const val TERMS = "$WEB_BASE/villkor"
}
