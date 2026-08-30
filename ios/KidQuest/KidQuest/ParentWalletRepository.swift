import Foundation

/// The wallet calls that name the child they are about.
///
/// `WalletRepository` speaks for whoever holds the device token, which is right for a
/// child looking at their own wallet and wrong for a parent standing next to one: the
/// same "wallet/expense" call from a parent's phone charges the PARENT's wallet. The
/// server has member-scoped twins of these routes for exactly that reason, and this is
/// where they live.
enum ParentWalletRepository {

    static func fetchBalance(memberId: String) async throws -> WalletBalanceResponseDTO {
        try await ApiClient.shared.send(
            WalletBalanceResponseDTO.self,
            path: "wallet/members/\(memberId)/balance",
            method: "GET"
        )
    }

    static func fetchTransactions(memberId: String, limit: Int = 20) async throws -> [WalletTransactionResponseDTO] {
        try await ApiClient.shared.send(
            [WalletTransactionResponseDTO].self,
            path: "wallet/members/\(memberId)/transactions",
            method: "GET",
            queryItems: [URLQueryItem(name: "limit", value: String(limit))]
        )
    }

    /// A purchase recorded on the child's behalf, against the child's wallet.
    static func recordExpense(
        memberId: String,
        amount: Int,
        description: String?,
        categoryId: String?
    ) async throws {
        let body = RecordExpenseRequestDTO(
            amount: amount,
            description: description,
            categoryId: categoryId
        )
        try await ApiClient.shared.sendWithoutResponse(
            path: "wallet/members/\(memberId)/expense",
            method: "POST",
            body: body
        )
    }
}
