package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Automatisk vecko- eller månadspeng för ett barn.
 *
 * Bara en förälder kommer åt det här, kontrollerat på servern -- att raden är dold i
 * barnets vy är designen, inte spärren.
 */
data class RecurringAllowanceResponse(
    val memberId: String,
    /** WEEKLY, MONTHLY eller LEVEL. */
    val kind: String,
    val amount: Int?,
    /** 1 = måndag ... 7 = söndag, samma som java.time. */
    val weekday: Int?,
    val dayOfMonth: Int?,
    val level1: Int?,
    val level2: Int?,
    val level3: Int?,
    val level4: Int?,
    val level5: Int?,
    val active: Boolean,
    /** ISO-datum, yyyy-MM-dd. */
    val nextDueOn: String?,
)

data class SaveRecurringAllowanceRequest(
    val kind: String,
    val amount: Int? = null,
    val weekday: Int? = null,
    val dayOfMonth: Int? = null,
    val level1: Int? = null,
    val level2: Int? = null,
    val level3: Int? = null,
    val level4: Int? = null,
    val level5: Int? = null,
)

interface RecurringAllowanceApi {

    /**
     * Svarar 204 när barnet inte har någon automatisk utbetalning, därav [Response] --
     * en tom kropp går inte att avkoda till ett objekt.
     */
    @GET("wallet/members/{memberId}/recurring-allowance")
    suspend fun get(@Path("memberId") memberId: String): Response<RecurringAllowanceResponse>

    @PUT("wallet/members/{memberId}/recurring-allowance")
    suspend fun save(
        @Path("memberId") memberId: String,
        @Body body: SaveRecurringAllowanceRequest,
    ): RecurringAllowanceResponse

    @DELETE("wallet/members/{memberId}/recurring-allowance")
    suspend fun disable(@Path("memberId") memberId: String): Response<Unit>
}
