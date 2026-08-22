package se.kidquest.app.billing

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.logOutWith

/**
 * Purchase identity, kept in one place.
 *
 * The App User ID is the *family* id, never a member id. Entitlement is bought once
 * per household: one parent subscribes and the children's device tokens -- which have
 * no store account of their own -- are covered by the same purchase. Using a member id
 * would sell the same family the app once per person.
 *
 * That also matches the backend, where family_subscription is keyed by family id, so a
 * webhook carrying the App User ID maps straight onto the row it needs to update.
 */
object Billing {

    private const val TAG = "Billing"

    fun configure(application: Application) {
        if (!BillingConfig.isConfigured) {
            Log.i(TAG, "No RevenueCat key set; skipping Purchases configuration.")
            return
        }
        val debuggable =
            (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Purchases.logLevel = if (debuggable) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(application, BillingConfig.REVENUECAT_PUBLIC_KEY)
                .build()
        )
    }

    /**
     * Ties purchases to a family. Called after any login that establishes one.
     *
     * Safe to call repeatedly with the same id. Failures are logged rather than
     * surfaced: a family that cannot reach RevenueCat should still be able to use the
     * app, because the server decides entitlement regardless.
     */
    fun identify(familyId: String?) {
        if (!BillingConfig.isConfigured || familyId.isNullOrBlank()) return
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.logInWith(
            appUserID = familyId,
            onError = { error ->
                Log.w(TAG, "Could not identify family $familyId: ${error.message}")
            },
            onSuccess = { _, created ->
                Log.i(TAG, "Identified family $familyId (new to RevenueCat: $created)")
            },
        )
    }

    /**
     * Detaches the device from a family on sign-out, so a subsequent login on the same
     * phone -- a different parent, or a child pairing by QR -- does not inherit the
     * previous family's purchases.
     */
    fun forget() {
        if (!BillingConfig.isConfigured || !Purchases.isConfigured) return
        Purchases.sharedInstance.logOutWith(
            onError = { error ->
                Log.w(TAG, "Could not log out of RevenueCat: ${error.message}")
            },
            onSuccess = { },
        )
    }
}
