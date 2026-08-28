package se.kidquest.app.billing

/**
 * RevenueCat configuration.
 *
 * The public SDK key is deliberately in source. RevenueCat's Android keys are
 * designed to ship inside the app binary -- anyone can read it out of an APK, and
 * it grants nothing beyond what the app itself can do. The secret worth guarding is
 * the webhook signing secret, which lives on the backend and never comes near here.
 *
 * Find the key in RevenueCat under API keys; the Google Play one starts with "goog_".
 * A key starting with "test_" belongs to the Test Store and cannot see real Play
 * purchases.
 */
object BillingConfig {

    /**
     * The Play-backed key. Safe in source: RevenueCat's Android keys are designed to
     * ship inside the app binary, and this one grants nothing the app cannot already do.
     */
    const val REVENUECAT_PUBLIC_KEY = "goog_UaVOtPGIgDOIWCPXhpovVWurAKM"

    /**
     * The entitlement identifier configured in RevenueCat. Anything the paywall gates
     * on checks this, but the server's own answer is what actually decides access --
     * see SubscriptionService. This is for what to show, not what to allow.
     */
    const val ENTITLEMENT_PRO = "pro"

    val isConfigured: Boolean get() = REVENUECAT_PUBLIC_KEY.isNotBlank()
}
