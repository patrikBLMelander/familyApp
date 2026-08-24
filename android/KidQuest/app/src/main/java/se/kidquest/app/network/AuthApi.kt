package se.kidquest.app.network

import retrofit2.http.Body
import retrofit2.http.POST

// Data-modeller baserade på frontendens family.ts

data class FamilyResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val updatedAt: String,
)

data class FamilyMemberResponse(
    val id: String,
    val name: String,
    val deviceToken: String?,
    val email: String?,
    val role: String,
    /** Purchases are identified by family, not by member. Nullable for safety. */
    val familyId: String? = null,
    /** Whether a device is paired, without exposing the token that would let you be them. */
    val hasPairedDevice: Boolean = false,
)

data class FamilyRegistrationResponse(
    val family: FamilyResponse,
    val admin: FamilyMemberResponse,
    val deviceToken: String,
)

data class EmailLoginResponse(
    val member: FamilyMemberResponse,
    val deviceToken: String,
)

data class RegisterFamilyRequest(
    val familyName: String,
    val adminName: String,
    val adminEmail: String,
    val password: String,
)

data class EmailLoginRequest(
    val email: String,
    val password: String,
)

interface AuthApi {

    @POST("families/register")
    suspend fun registerFamily(
        @Body body: RegisterFamilyRequest,
    ): FamilyRegistrationResponse

    @POST("families/login-by-email")
    suspend fun loginByEmail(
        @Body body: EmailLoginRequest,
    ): EmailLoginResponse
}

