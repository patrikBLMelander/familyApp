package se.kidquest.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class WalletBalanceResponse(
    val id: String?,
    val memberId: String,
    val balance: Int,
)

data class WalletTransactionResponse(
    val id: String,
    val walletId: String,
    val amount: Int,
    val transactionType: String,
    val description: String?,
    val categoryId: String?,
    val createdByMemberId: String?,
    val isDeleted: Boolean?,
    val deletedAt: String?,
    val deletedByMemberId: String?,
    val createdAt: String,
)

data class AddAllowanceRequest(
    val childMemberId: String,
    val amount: Int,
    val description: String,
    val savingsGoalAllocations: List<SavingsGoalAllocationRequest>? = null,
)

data class SavingsGoalAllocationRequest(
    val savingsGoalId: String,
    val amount: Int,
)

data class RecordExpenseRequest(
    val amount: Int,
    val description: String?,
    val categoryId: String?,
    val savingsGoalAllocations: List<SavingsGoalAllocationRequest>? = null,
)

data class ExpenseCategoryResponse(
    val id: String,
    val name: String,
    val emoji: String?,
    val isDefault: Boolean,
)

data class SavingsGoalResponse(
    val id: String,
    val memberId: String,
    val name: String,
    val targetAmount: Int,
    val currentAmount: Int,
    val emoji: String?,
    val isActive: Boolean,
    val isCompleted: Boolean,
    val isPurchased: Boolean,
    val completedAt: String?,
    val purchasedAt: String?,
    val purchaseTransactionId: String?,
    val progressPercentage: Int,
    val remainingAmount: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class CreateSavingsGoalRequest(
    val name: String,
    val targetAmount: Int,
    val emoji: String? = null,
)

data class AllocateToGoalsRequest(
    val savingsGoalAllocations: List<SavingsGoalAllocationRequest>,
)

interface WalletApi {
    @GET("wallet/balance")
    suspend fun getWalletBalance(): WalletBalanceResponse

    @GET("wallet/members/{memberId}/balance")
    suspend fun getMemberBalance(@Path("memberId") memberId: String): WalletBalanceResponse

    @GET("wallet/transactions")
    suspend fun getTransactions(@Query("limit") limit: Int = 20): List<WalletTransactionResponse>

    @GET("wallet/members/{memberId}/transactions")
    suspend fun getMemberTransactions(
        @Path("memberId") memberId: String,
        @Query("limit") limit: Int = 20,
    ): List<WalletTransactionResponse>

    @POST("wallet/allowance")
    suspend fun addAllowance(@Body body: AddAllowanceRequest): Response<Unit>

    @POST("wallet/expense")
    suspend fun recordExpense(@Body body: RecordExpenseRequest): Response<Unit>

    @POST("wallet/members/{memberId}/expense")
    suspend fun recordExpenseForMember(
        @Path("memberId") memberId: String,
        @Body body: RecordExpenseRequest,
    ): Response<Unit>

    @GET("wallet/categories")
    suspend fun getExpenseCategories(): List<ExpenseCategoryResponse>

    @GET("wallet/savings-goals")
    suspend fun getSavingsGoals(): List<SavingsGoalResponse>

    @GET("wallet/savings-goals/active")
    suspend fun getActiveSavingsGoals(): List<SavingsGoalResponse>

    @POST("wallet/savings-goals")
    suspend fun createSavingsGoal(@Body body: CreateSavingsGoalRequest): SavingsGoalResponse

    @DELETE("wallet/savings-goals/{goalId}")
    suspend fun deleteSavingsGoal(@Path("goalId") goalId: String): Response<Unit>

    @POST("wallet/allocate-to-goals")
    suspend fun allocateToGoals(@Body body: AllocateToGoalsRequest): Response<Unit>
}
