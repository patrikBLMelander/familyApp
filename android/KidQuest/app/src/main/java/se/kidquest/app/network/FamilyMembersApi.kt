package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

data class CreateFamilyMemberRequest(
    val name: String,
    val role: String, // "CHILD" | "PARENT" | "ASSISTANT"
)

data class UpdateFamilyMemberRequest(
    val name: String,
)

data class UpdatePasswordRequest(
    val password: String,
)

data class InviteTokenResponse(
    val token: String,
)

data class LinkDeviceByTokenRequest(
    val inviteToken: String,
    val deviceToken: String,
)

interface FamilyMembersApi {
    // Resolves who a stored token belongs to. Only needed for sessions written
    // before the role was persisted locally; new logins never call it.
    // TODO: replace with a header-based /family-members/me so the token stops
    // travelling in a URL path, where proxies log it.
    @GET("family-members/by-device-token/{deviceToken}")
    suspend fun getMemberByDeviceToken(
        @Path("deviceToken") deviceToken: String,
    ): FamilyMemberResponse

    @GET("family-members")
    suspend fun getAllMembers(): List<FamilyMemberResponse>

    @POST("family-members")
    suspend fun createMember(@Body body: CreateFamilyMemberRequest): FamilyMemberResponse

    @PATCH("family-members/{memberId}")
    suspend fun updateMember(
        @Path("memberId") memberId: String,
        @Body body: UpdateFamilyMemberRequest,
    ): FamilyMemberResponse

    /**
     * Sets a member's password. A parent may do this for any adult in the family,
     * which is the whole recovery path when someone is locked out -- there is no
     * email reset yet.
     *
     * Does not invalidate existing sessions: whoever is locked out has none, and
     * signing the other parent's phone out would be gratuitous.
     */
    @PATCH("family-members/{memberId}/password")
    suspend fun updatePassword(
        @Path("memberId") memberId: String,
        @Body body: UpdatePasswordRequest,
    ): FamilyMemberResponse

    /**
     * Irreversible: takes the member's chores, completions, XP, pets and wallet
     * history with them. Callers must confirm before reaching this.
     */
    @DELETE("family-members/{memberId}")
    suspend fun deleteMember(
        @Path("memberId") memberId: String,
    ): Response<Unit>

    @POST("family-members/{memberId}/generate-invite")
    suspend fun generateInviteToken(
        @Path("memberId") memberId: String,
    ): InviteTokenResponse

    @POST("family-members/link-device-by-token")
    suspend fun linkDeviceByInviteToken(
        @Body body: LinkDeviceByTokenRequest,
    ): FamilyMemberResponse
}
