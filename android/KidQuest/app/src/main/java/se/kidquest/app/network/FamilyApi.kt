package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Path

/**
 * Family-level operations. Currently one: deleting the whole thing.
 *
 * Both stores require an in-app route to account deletion, and Apple enforces it
 * strictly. Deliberately not behind the entitlement guard -- a family must always be
 * able to leave, whether or not they have paid.
 */
interface FamilyApi {

    @DELETE("families/{familyId}")
    suspend fun deleteFamily(@Path("familyId") familyId: String): Response<Unit>
}
