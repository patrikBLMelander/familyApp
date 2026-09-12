package se.kidquest.app.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Ett äventyr. Klockan är server-styrd: `secondsRemaining` och `ready` räknas på servern
 * ur `startedAt` + `durationSecs`, aldrig ur telefonens klocka. `loot*` är null tills det
 * hämtats. Tidsstämplar är ISO-strängar (parsas inte här).
 */
data class AdventureResponse(
    val id: String,
    val scene: String,
    val status: String, // ONGOING | CLAIMED
    val durationSecs: Int,
    val secondsRemaining: Long,
    val ready: Boolean,
    val lootType: String?, // FOOD | FRAME | EGG
    val lootRef: String?,
    val lootQty: Int?,
    val startedAt: String,
)

data class AdventureStateResponse(
    val ticketBalance: Long,
    val adventures: List<AdventureResponse>,
)

data class StartAdventureRequest(
    val scene: String,
)

/** Utfallet av att hämta ett äventyr. */
data class LootResponse(
    val type: String, // FOOD | FRAME | EGG
    val ref: String,  // mat = "food", ram = loot_item-id, ägg = egg_type
    val quantity: Int,
)

data class InventoryItemResponse(
    val itemId: String,
    val acquiredAt: String,
)

interface AdventuresApi {
    @GET("adventures")
    suspend fun getState(): AdventureStateResponse

    @POST("adventures")
    suspend fun start(@Body body: StartAdventureRequest): AdventureResponse

    @POST("adventures/{id}/claim")
    suspend fun claim(@Path("id") id: String): LootResponse

    @GET("adventures/inventory")
    suspend fun getInventory(): List<InventoryItemResponse>

    // --- Member-scoped: en förälder som agerar i barnets vy. ---

    @GET("adventures/members/{memberId}")
    suspend fun getStateForMember(@Path("memberId") memberId: String): AdventureStateResponse

    @POST("adventures/members/{memberId}")
    suspend fun startForMember(
        @Path("memberId") memberId: String,
        @Body body: StartAdventureRequest,
    ): AdventureResponse

    @POST("adventures/members/{memberId}/{id}/claim")
    suspend fun claimForMember(
        @Path("memberId") memberId: String,
        @Path("id") id: String,
    ): LootResponse

    @GET("adventures/members/{memberId}/inventory")
    suspend fun getInventoryForMember(@Path("memberId") memberId: String): List<InventoryItemResponse>
}
