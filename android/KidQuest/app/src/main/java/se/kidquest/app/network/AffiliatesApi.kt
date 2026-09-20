package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RedeemReferralRequest(val code: String)

interface AffiliatesApi {

    /**
     * Attribute the caller's family to the affiliate owning [code]. First-touch on the
     * server: an already-attributed family is left alone (still 204). 400 means the code
     * is unknown. The device token (and thus the family) comes from the auth interceptor.
     */
    @POST("affiliates/redeem")
    suspend fun redeem(@Body body: RedeemReferralRequest): Response<Unit>
}
