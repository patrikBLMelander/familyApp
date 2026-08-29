package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Family-level operations.
 *
 * Both stores require an in-app route to account deletion, and Apple enforces it
 * strictly. Deliberately not behind the entitlement guard -- a family must always be
 * able to leave, whether or not they have paid.
 */
interface FamilyApi {

    /** The name the family gave itself at registration, shown as the dashboard's title. */
    @GET("families/{familyId}")
    suspend fun getFamily(@Path("familyId") familyId: String): Response<FamilyResponse>

    @DELETE("families/{familyId}")
    suspend fun deleteFamily(@Path("familyId") familyId: String): Response<Unit>
}
