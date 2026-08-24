package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class PetResponse(
    val id: String,
    val memberId: String,
    val year: Int,
    val month: Int,
    val selectedEggType: String,
    val petType: String,
    val name: String?,
    val growthStage: Int,
    val hatchedAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class SelectEggRequest(
    val eggType: String,
    val name: String?,
)

data class FoodItemResponse(
    val id: String,
    val eventId: String?,
    val xpAmount: Int,
    val collectedAt: String,
)

data class CollectedFoodResponse(
    val foodItems: List<FoodItemResponse>,
    val totalCount: Int,
)

data class LastFedDateResponse(
    val lastFedDate: String?,
)

data class FeedPetRequest(
    val xpAmount: Int,
)

interface PetsApi {
    @GET("pets/current")
    suspend fun getCurrentPet(): Response<PetResponse>

    @GET("pets/available-eggs")
    suspend fun getAvailableEggTypes(): List<String>

    @POST("pets/select-egg")
    suspend fun selectEgg(@Body body: SelectEggRequest): PetResponse

    @GET("pets/members/{memberId}/current")
    suspend fun getMemberPet(@Path("memberId") memberId: String): Response<PetResponse>

    @GET("pets/collected-food")
    suspend fun getCollectedFood(): CollectedFoodResponse

    @POST("pets/feed")
    suspend fun feedPet(@Body body: FeedPetRequest): Response<Unit>

    // --- Acting for another member, for a child on a parent's phone. Authorised
    // server-side as a parent of the same family; see PetController.requireParentOf.

    @POST("pets/members/{memberId}/select-egg")
    suspend fun selectEggForMember(
        @Path("memberId") memberId: String,
        @Body body: SelectEggRequest,
    ): PetResponse

    @POST("pets/members/{memberId}/feed")
    suspend fun feedMemberPet(
        @Path("memberId") memberId: String,
        @Body body: FeedPetRequest,
    ): Response<Unit>

    @GET("pets/members/{memberId}/collected-food")
    suspend fun getMemberCollectedFood(
        @Path("memberId") memberId: String,
    ): CollectedFoodResponse

    @GET("pets/members/{memberId}/last-fed-date")
    suspend fun getMemberLastFedDate(
        @Path("memberId") memberId: String,
    ): LastFedDateResponse
}
