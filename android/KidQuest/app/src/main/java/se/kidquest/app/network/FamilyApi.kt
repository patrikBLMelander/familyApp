package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * Family-level operations.
 *
 * Both stores require an in-app route to account deletion, and Apple enforces it
 * strictly. Deliberately not behind the entitlement guard -- a family must always be
 * able to leave, whether or not they have paid.
 */
data class UpdateCurrencyRequest(val currency: String)

interface FamilyApi {

    /** The name the family gave itself at registration, shown as the dashboard's title. */
    @GET("families/{familyId}")
    suspend fun getFamily(@Path("familyId") familyId: String): Response<FamilyResponse>

    /** Parents only: the currency every wallet amount in the family is shown in. */
    @PATCH("families/{familyId}/currency")
    suspend fun updateCurrency(
        @Path("familyId") familyId: String,
        @Body body: UpdateCurrencyRequest,
    ): FamilyResponse

    @DELETE("families/{familyId}")
    suspend fun deleteFamily(@Path("familyId") familyId: String): Response<Unit>
}
