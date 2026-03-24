import Foundation

// MARK: - Auth

struct FamilyResponseDTO: Decodable {
    let id: String
    let name: String
    let createdAt: String
    let updatedAt: String
}

struct FamilyMemberResponseDTO: Decodable {
    let id: String
    let name: String
    let deviceToken: String?
    let email: String?
    let role: String
}

struct FamilyRegistrationResponseDTO: Decodable {
    let family: FamilyResponseDTO
    let admin: FamilyMemberResponseDTO
    let deviceToken: String
}

struct EmailLoginResponseDTO: Decodable {
    let member: FamilyMemberResponseDTO
    let deviceToken: String
}

struct RegisterFamilyRequestDTO: Encodable {
    let familyName: String
    let adminName: String
    let adminEmail: String
    let password: String
}

struct EmailLoginRequestDTO: Encodable {
    let email: String
    let password: String
}

// MARK: - Family members

struct CreateFamilyMemberRequestDTO: Encodable {
    let name: String
    let role: String
}

struct InviteTokenResponseDTO: Decodable {
    let token: String
}

struct LinkDeviceByTokenRequestDTO: Encodable {
    let inviteToken: String
    let deviceToken: String
}

// MARK: - Calendar / Tasks

enum RecurringTypeDTO: String, Decodable, Encodable {
    case DAILY, WEEKLY, MONTHLY, YEARLY
}

struct CalendarEventResponseDTO: Decodable {
    let id: String
    let familyId: String
    let categoryId: String?
    let title: String
    let description: String?
    let startDateTime: String
    let endDateTime: String?
    let isAllDay: Bool
    let location: String?
    let createdById: String
    let recurringType: RecurringTypeDTO?
    let recurringInterval: Int?
    let recurringEndDate: String?
    let recurringEndCount: Int?
    let isTask: Bool
    let xpPoints: Int?
    let isRequired: Bool
    let createdAt: String
    let updatedAt: String
    let participantIds: [String]
}

struct CalendarEventTaskCompletionResponseDTO: Decodable {
    let id: String
    let eventId: String
    let memberId: String
    let occurrenceDate: String
    let completedAt: String
}

struct CalendarTaskWithCompletionDTO: Decodable {
    let event: CalendarEventResponseDTO
    let completed: Bool
}

struct MarkTaskCompletedRequestDTO: Encodable {
    let memberId: String?
    let occurrenceDate: String
}

// MARK: - Daily Chores

struct DailyChoreResponseDTO: Decodable {
    let id: String
    let memberId: String
    let title: String
    let weekdays: [String]
    let xpPoints: Int
    let isActive: Bool
}

struct DailyChoreWithCompletionResponseDTO: Decodable {
    let chore: DailyChoreResponseDTO
    let completed: Bool
    let completionId: String?
}

struct MarkChoreCompletedRequestDTO: Encodable {
    let date: String
}

struct CreateDailyChoreRequestDTO: Encodable {
    let memberId: String
    let title: String
    let weekdays: [String]
    let xpPoints: Int
}

// MARK: - Pets / XP / Wallet

struct PetResponseDTO: Decodable {
    let id: String
    let memberId: String
    let year: Int
    let month: Int
    let selectedEggType: String
    let petType: String
    let name: String?
    let growthStage: Int
    let hatchedAt: String?
    let createdAt: String
    let updatedAt: String
}

struct CollectedFoodResponseDTO: Decodable {
    let foodItems: [FoodItemResponseDTO]
    let totalCount: Int
}

struct FoodItemResponseDTO: Decodable {
    let id: String
    let eventId: String?
    let xpAmount: Int
    let collectedAt: String
}

struct FeedPetRequestDTO: Encodable {
    let xpAmount: Int
}

struct SelectEggRequestDTO: Encodable {
    let eggType: String
    let name: String?
}

struct XpProgressResponseDTO: Decodable {
    let id: String
    let memberId: String
    let year: Int
    let month: Int
    let currentXp: Int
    let currentLevel: Int
    let totalTasksCompleted: Int
    let xpForNextLevel: Int
    let xpInCurrentLevel: Int
}

struct WalletBalanceResponseDTO: Decodable {
    let id: String?
    let memberId: String
    let balance: Int
}

struct WalletTransactionResponseDTO: Decodable, Identifiable {
    let id: String
    let walletId: String
    let amount: Int
    let transactionType: String
    let description: String?
    let categoryId: String?
    let createdByMemberId: String?
    let isDeleted: Bool?
    let deletedAt: String?
    let deletedByMemberId: String?
    let createdAt: String
}

struct SavingsGoalResponseDTO: Decodable, Identifiable {
    let id: String
    let memberId: String
    let name: String
    let targetAmount: Int
    let currentAmount: Int
    let emoji: String?
    let isActive: Bool
    let isCompleted: Bool
    let isPurchased: Bool
    let progressPercentage: Int
    let remainingAmount: Int
    let createdAt: String
    let updatedAt: String
}

struct CreateSavingsGoalRequestDTO: Encodable {
    let name: String
    let targetAmount: Int
    let emoji: String?
}

struct RecordExpenseRequestDTO: Encodable {
    let amount: Int
    let description: String?
    let categoryId: String?
}

struct AddAllowanceRequestDTO: Encodable {
    let childMemberId: String
    let amount: Int
    let description: String
}

struct SavingsGoalAllocationRequestDTO: Encodable {
    let savingsGoalId: String
    let amount: Int
}

struct AllocateToGoalsRequestDTO: Encodable {
    let savingsGoalAllocations: [SavingsGoalAllocationRequestDTO]
}

struct AwardBonusXpRequestDTO: Encodable {
    let xpPoints: Int
}

struct ExpenseCategoryResponseDTO: Decodable, Identifiable {
    let id: String
    let name: String
    let emoji: String?
    let isDefault: Bool
}

struct WalletNotificationResponseDTO: Decodable, Identifiable {
    let id: String
    let memberId: String
    let transactionId: String
    let amount: Int
    let description: String?
    let shownAt: String?
    let createdAt: String
}


