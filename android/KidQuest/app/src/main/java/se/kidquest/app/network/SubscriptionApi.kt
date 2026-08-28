package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.GET

/**
 * The family's billing state, as the server sees it.
 *
 * [entitled] is the only field to gate on. Everything else describes what to *say* --
 * the server enforces the same answer on every write regardless, so an app that got
 * this wrong could show the wrong banner but never hand out access it should not.
 */
data class SubscriptionStatusResponse(
    /** TRIAL, ACTIVE, GRACE, CANCELED, EXPIRED or COMPED. */
    val status: String,
    val entitled: Boolean,
    val trialEndsAt: String?,
    val trialDaysRemaining: Long,
    val inTrial: Boolean,
    val currentPeriodEnd: String?,
    val platform: String?,
    val cancelAtPeriodEnd: Boolean,
    /** Free access granted by hand. Never nag a comped family. */
    val comped: Boolean,
)

interface SubscriptionApi {

    @GET("subscription/status")
    suspend fun getStatus(): Response<SubscriptionStatusResponse>
}
