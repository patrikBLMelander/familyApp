import Foundation

// MARK: - Auth repository

enum AuthRepository {
    static func registerFamily(
        familyName: String,
        adminName: String,
        email: String,
        password: String
    ) async throws {
        let body = RegisterFamilyRequestDTO(
            familyName: familyName,
            adminName: adminName,
            adminEmail: email,
            password: password
        )
        let response = try await ApiClient.shared.send(
            FamilyRegistrationResponseDTO.self,
            path: "families/register",
            method: "POST",
            body: body
        )
        TokenStoreIOS.shared.setToken(response.deviceToken)
    }

    static func login(email: String, password: String) async throws {
        let body = EmailLoginRequestDTO(email: email, password: password)
        let response = try await ApiClient.shared.send(
            EmailLoginResponseDTO.self,
            path: "families/login-by-email",
            method: "POST",
            body: body
        )
        TokenStoreIOS.shared.setToken(response.deviceToken)
    }
}

// MARK: - Family members

enum FamilyRepository {
    static func fetchChildren() async throws -> [FamilyMemberResponseDTO] {
        let members = try await ApiClient.shared.send(
            [FamilyMemberResponseDTO].self,
            path: "family-members",
            method: "GET"
        )
        return members.filter { $0.role == "CHILD" || $0.role == "ASSISTANT" }
    }

    static func linkDeviceByInviteToken(inviteToken: String) async throws -> FamilyMemberResponseDTO {
        let deviceToken = UUID().uuidString
        let body = LinkDeviceByTokenRequestDTO(inviteToken: inviteToken, deviceToken: deviceToken)
        let member = try await ApiClient.shared.send(
            FamilyMemberResponseDTO.self,
            path: "family-members/link-device-by-token",
            method: "POST",
            body: body
        )
        TokenStoreIOS.shared.setToken(deviceToken)
        return member
    }

    static func generateInviteToken(forMemberId memberId: String) async throws -> String {
        let response = try await ApiClient.shared.send(
            InviteTokenResponseDTO.self,
            path: "family-members/\(memberId)/generate-invite",
            method: "POST"
        )
        return response.token
    }
}

// MARK: - Calendar / tasks

enum CalendarRepositoryIOS {
    static func fetchTasksForToday(memberId: String) async throws -> [CalendarTaskWithCompletionDTO] {
        let today = Date()
        let calendar = Calendar(identifier: .gregorian)
        let startOfDay = calendar.startOfDay(for: today)
        guard let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay) else {
            return []
        }

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"

        let startString = formatter.string(from: startOfDay)
        let endString = formatter.string(from: endOfDay.addingTimeInterval(-1))

        async let eventsReq = ApiClient.shared.send(
            [CalendarEventResponseDTO].self,
            path: "calendar/events",
            method: "GET",
            queryItems: [
                URLQueryItem(name: "startDate", value: startString),
                URLQueryItem(name: "endDate", value: endString),
            ]
        )
        async let completionsReq = ApiClient.shared.send(
            [CalendarEventTaskCompletionResponseDTO].self,
            path: "calendar/members/\(memberId)/task-completions",
            method: "GET"
        )

        let (events, completions) = try await (eventsReq, completionsReq)
        let taskEvents = events.filter { $0.isTask && $0.participantIds.contains(memberId) }

        formatter.dateFormat = "yyyy-MM-dd"
        let dateStr = formatter.string(from: today)

        let completionMap = Dictionary(
            uniqueKeysWithValues: completions
                .filter { $0.occurrenceDate == dateStr }
                .map { ($0.eventId, $0) }
        )

        return taskEvents.map { event in
            CalendarTaskWithCompletionDTO(
                event: event,
                completed: completionMap[event.id] != nil
            )
        }
    }

    static func toggleTaskCompletion(eventId: String, memberId: String, isCompleted: Bool) async throws {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let dateStr = formatter.string(from: Date())

        if isCompleted {
            try await ApiClient.shared.sendWithoutResponse(
                path: "calendar/events/\(eventId)/task-completion",
                method: "DELETE",
                queryItems: [
                    URLQueryItem(name: "memberId", value: memberId),
                    URLQueryItem(name: "occurrenceDate", value: dateStr)
                ]
            )
        } else {
            let body = MarkTaskCompletedRequestDTO(memberId: memberId, occurrenceDate: dateStr)
            try await ApiClient.shared.sendWithoutResponse(
                path: "calendar/events/\(eventId)/task-completion",
                method: "POST",
                body: body
            )
        }
    }
}

// MARK: - Pets / XP / Wallet (för barn-dashboard)

enum ChildDashboardRepository {
    struct Summary {
        let pet: PetResponseDTO?
        let xp: XpProgressResponseDTO?
        let wallet: WalletBalanceResponseDTO?
        let collectedFood: CollectedFoodResponseDTO?
        let todaysTasks: [CalendarTaskWithCompletionDTO]
    }

    static func fetchSummaryForCurrentMember(childId: String) async throws -> Summary {
        async let petResp: PetResponseDTO? = try? ApiClient.shared
            .send(PetResponseDTO.self, path: "pets/current", method: "GET")
        async let xpResp: XpProgressResponseDTO? = try? ApiClient.shared
            .send(XpProgressResponseDTO.self, path: "xp/current", method: "GET")
        async let walletResp: WalletBalanceResponseDTO? = try? ApiClient.shared
            .send(WalletBalanceResponseDTO.self, path: "wallet/balance", method: "GET")
        async let foodResp: CollectedFoodResponseDTO? = try? ApiClient.shared
            .send(CollectedFoodResponseDTO.self, path: "pets/collected-food", method: "GET")
        async let todaysTasks = try CalendarRepositoryIOS.fetchTasksForToday(memberId: childId)

        return try await Summary(
            pet: petResp,
            xp: xpResp,
            wallet: walletResp,
            collectedFood: foodResp,
            todaysTasks: todaysTasks
        )
    }

    static func feedPet(xpAmount: Int) async throws {
        let body = FeedPetRequestDTO(xpAmount: xpAmount)
        try await ApiClient.shared.sendWithoutResponse(
            path: "pets/feed",
            method: "POST",
            body: body
        )
    }

    static func selectEgg(eggType: String, name: String?) async throws -> PetResponseDTO {
        let body = SelectEggRequestDTO(eggType: eggType, name: name?.isEmpty == false ? name : nil)
        return try await ApiClient.shared.send(
            PetResponseDTO.self,
            path: "pets/select-egg",
            method: "POST",
            body: body
        )
    }
}

// MARK: - Wallet

enum WalletRepository {
    static func fetchSavingsGoals() async throws -> [SavingsGoalResponseDTO] {
        try await ApiClient.shared.send([SavingsGoalResponseDTO].self, path: "wallet/savings-goals", method: "GET")
    }

    static func createSavingsGoal(name: String, targetAmount: Int, emoji: String?) async throws -> SavingsGoalResponseDTO {
        let body = CreateSavingsGoalRequestDTO(name: name, targetAmount: targetAmount, emoji: emoji)
        return try await ApiClient.shared.send(SavingsGoalResponseDTO.self, path: "wallet/savings-goals", method: "POST", body: body)
    }

    static func recordExpense(amount: Int, description: String?, categoryId: String?) async throws {
        let body = RecordExpenseRequestDTO(amount: amount, description: description, categoryId: categoryId)
        try await ApiClient.shared.sendWithoutResponse(path: "wallet/expense", method: "POST", body: body)
    }

    static func fetchExpenseCategories() async throws -> [ExpenseCategoryResponseDTO] {
        try await ApiClient.shared.send([ExpenseCategoryResponseDTO].self, path: "wallet/categories", method: "GET")
    }

    static func fetchUnshownNotifications() async throws -> [WalletNotificationResponseDTO] {
        try await ApiClient.shared.send([WalletNotificationResponseDTO].self, path: "wallet/notifications/unshown", method: "GET")
    }

    static func markNotificationShown(notificationId: String) async throws {
        try await ApiClient.shared.sendWithoutResponse(path: "wallet/notifications/\(notificationId)/mark-shown", method: "POST")
    }

    static func giveAllowance(childMemberId: String, amount: Int, description: String) async throws {
        let body = AddAllowanceRequestDTO(childMemberId: childMemberId, amount: amount, description: description)
        try await ApiClient.shared.sendWithoutResponse(path: "wallet/allowance", method: "POST", body: body)
    }

    static func allocateToGoals(allocations: [(goalId: String, amount: Int)]) async throws {
        let body = AllocateToGoalsRequestDTO(
            savingsGoalAllocations: allocations.map { SavingsGoalAllocationRequestDTO(savingsGoalId: $0.goalId, amount: $0.amount) }
        )
        try await ApiClient.shared.sendWithoutResponse(path: "wallet/allocate-to-goals", method: "POST", body: body)
    }
}

// MARK: - Adult pet view

enum PetRepository {
    static func fetchPetForMember(memberId: String) async throws -> PetResponseDTO {
        try await ApiClient.shared.send(
            PetResponseDTO.self,
            path: "pets/members/\(memberId)/current",
            method: "GET"
        )
    }

    static func awardBonusXp(memberId: String, xpPoints: Int) async throws {
        let body = AwardBonusXpRequestDTO(xpPoints: xpPoints)
        try await ApiClient.shared.sendWithoutResponse(
            path: "xp/members/\(memberId)/bonus",
            method: "POST",
            body: body
        )
    }
}

