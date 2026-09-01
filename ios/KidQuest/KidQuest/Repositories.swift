import Foundation

// MARK: - Family members

enum FamilyRepository {
    static func fetchAllMembers() async throws -> [FamilyMemberResponseDTO] {
        try await ApiClient.shared.send(
            [FamilyMemberResponseDTO].self,
            path: "family-members",
            method: "GET"
        )
    }

    static func fetchChildren() async throws -> [FamilyMemberResponseDTO] {
        try await fetchAllMembers().filter { $0.role == "CHILD" || $0.role == "ASSISTANT" }
    }

    /// Familjens namn, valt vid registreringen. Tomt namn behandlas som inget namn,
    /// så att anroparen kan falla tillbaka på "Min familj".
    static func fetchFamilyName(familyId: String) async throws -> String? {
        let family = try await ApiClient.shared.send(
            FamilyResponseDTO.self,
            path: "families/\(familyId)",
            method: "GET"
        )
        let trimmed = family.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
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
        TokenStoreIOS.shared.setSession(
            deviceToken: deviceToken,
            memberId: member.id,
            memberName: member.name,
            role: member.role,
            familyId: member.familyId
        )
        return member
    }

    /// Roll är "CHILD" eller "PARENT". ASSISTANT finns kvar i backend för medlemmar
    /// som skapades förr, men delas inte ut längre -- samma val som Android gör.
    static func createMember(name: String, role: String) async throws -> FamilyMemberResponseDTO {
        try await ApiClient.shared.send(
            FamilyMemberResponseDTO.self,
            path: "family-members",
            method: "POST",
            body: CreateFamilyMemberRequestDTO(name: name, role: role)
        )
    }

    static func updateMemberName(memberId: String, name: String) async throws -> FamilyMemberResponseDTO {
        try await ApiClient.shared.send(
            FamilyMemberResponseDTO.self,
            path: "family-members/\(memberId)",
            method: "PATCH",
            body: UpdateFamilyMemberRequestDTO(name: name)
        )
    }

    /// Sätter en medlems lösenord. En förälder får göra det åt vilken vuxen som helst i
    /// familjen, vilket är hela vägen tillbaka in för den som låst ute sig -- det finns
    /// ingen återställning via e-post i appen.
    ///
    /// Befintliga sessioner avslutas inte: den som är utelåst har ingen, och att logga
    /// ut den andra förälderns telefon vore omotiverat.
    static func updatePassword(memberId: String, password: String) async throws {
        _ = try await ApiClient.shared.send(
            FamilyMemberResponseDTO.self,
            path: "family-members/\(memberId)/password",
            method: "PATCH",
            body: UpdatePasswordRequestDTO(password: password)
        )
    }

    /// Oåterkalleligt: tar medlemmens sysslor, avklaranden, XP, djur och plånbokshistorik
    /// med sig. Anroparen måste be om bekräftelse innan den här nås.
    static func deleteMember(memberId: String) async throws {
        try await ApiClient.shared.sendWithoutResponse(
            path: "family-members/\(memberId)",
            method: "DELETE"
        )
    }

    /// Raderar hela familjen och allt som hänger på den.
    ///
    /// Ligger medvetet inte bakom prenumerationsspärren -- en familj måste alltid kunna
    /// lämna, oavsett om de betalat eller inte. Apple kräver dessutom en väg till
    /// radering inifrån appen och avvisar utan.
    static func deleteFamily(familyId: String) async throws {
        try await ApiClient.shared.sendWithoutResponse(
            path: "families/\(familyId)",
            method: "DELETE"
        )
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

// MARK: - Daily Chores

enum DailyChoreRepositoryIOS {

    /// Everything a chore list needs, from the two endpoints that hold it.
    ///
    /// The screen should not have to know that "what is due today" and "what recurs on
    /// which weekday" are two different reads. `today` carries the completions and is
    /// the only list that can be ticked; `all` carries the schedule the week tab draws.
    struct Chores {
        var today: [DailyChoreWithCompletionResponseDTO]
        var all: [DailyChoreResponseDTO]
    }

    /// Both lists in one call, for one member.
    static func fetchChores(memberId: String, date: Date = Date()) async throws -> Chores {
        async let todayTask = fetchChoresForDate(memberId: memberId, date: apiDate(date))
        async let allTask = fetchAllChores(memberId: memberId)
        return try await Chores(today: todayTask, all: allTask)
    }

    /// Every active chore for the member, on every weekday — not only today's.
    static func fetchAllChores(memberId: String) async throws -> [DailyChoreResponseDTO] {
        try await ApiClient.shared.send(
            [DailyChoreResponseDTO].self,
            path: "daily-chores/members/\(memberId)",
            method: "GET"
        )
    }

    static func deleteChore(choreId: String) async throws {
        try await ApiClient.shared.sendWithoutResponse(
            path: "daily-chores/\(choreId)",
            method: "DELETE"
        )
    }

    /// The wire format the daily-chore endpoints expect. POSIX locale so a phone set to
    /// a non-Gregorian calendar still sends 2026-08-30.
    static func apiDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func fetchChoresForDate(memberId: String, date: String) async throws -> [DailyChoreWithCompletionResponseDTO] {
        try await ApiClient.shared.send(
            [DailyChoreWithCompletionResponseDTO].self,
            path: "daily-chores/members/\(memberId)/for-date",
            method: "GET",
            queryItems: [URLQueryItem(name: "date", value: date)]
        )
    }

    static func createChore(memberId: String, title: String, weekdays: [String], xpPoints: Int) async throws {
        let body = CreateDailyChoreRequestDTO(memberId: memberId, title: title, weekdays: weekdays, xpPoints: xpPoints)
        try await ApiClient.shared.sendWithoutResponse(path: "daily-chores", method: "POST", body: body)
    }

    static func toggleChoreCompletion(choreId: String, date: String, isCompleted: Bool) async throws {
        if isCompleted {
            try await ApiClient.shared.sendWithoutResponse(
                path: "daily-chores/\(choreId)/completion",
                method: "DELETE",
                queryItems: [URLQueryItem(name: "date", value: date)]
            )
        } else {
            let body = MarkChoreCompletedRequestDTO(date: date)
            try await ApiClient.shared.sendWithoutResponse(
                path: "daily-chores/\(choreId)/completion",
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
        let todaysTasks: [DailyChoreWithCompletionResponseDTO]
    }

    static func fetchSummaryForCurrentMember(childId: String) async throws -> Summary {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let dateStr = formatter.string(from: Date())

        async let petResp: PetResponseDTO? = try? ApiClient.shared
            .send(PetResponseDTO.self, path: "pets/current", method: "GET")
        async let xpResp: XpProgressResponseDTO? = try? ApiClient.shared
            .send(XpProgressResponseDTO.self, path: "xp/current", method: "GET")
        async let walletResp: WalletBalanceResponseDTO? = try? ApiClient.shared
            .send(WalletBalanceResponseDTO.self, path: "wallet/balance", method: "GET")
        async let foodResp: CollectedFoodResponseDTO? = try? ApiClient.shared
            .send(CollectedFoodResponseDTO.self, path: "pets/collected-food", method: "GET")
        async let todaysTasks = try DailyChoreRepositoryIOS.fetchChoresForDate(memberId: childId, date: dateStr)

        return try await Summary(
            pet: petResp,
            xp: xpResp,
            wallet: walletResp,
            collectedFood: foodResp,
            todaysTasks: todaysTasks
        )
    }

    /// Djuren barnet haft tidigare, nyast först.
    ///
    /// Sväljer felet: samlingen är en trevlighet, och en historik som inte svarar ska
    /// inte hindra barnet från att mata dagens djur.
    static func fetchPetHistory() async -> [PetHistoryResponseDTO] {
        let all = (try? await ApiClient.shared.send(
            [PetHistoryResponseDTO].self,
            path: "pets/history",
            method: "GET"
        )) ?? []
        return all.sorted { ($0.year, $0.month) > ($1.year, $1.month) }
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

    /// Den stående veckopengen eller månadspengen, eller nil när ingen är igång.
    ///
    /// Backend svarar 204 utan kropp när inget är inställt. Avkodningen av en tom
    /// kropp kastar, vilket är samma sak som "ingen peng inställd" här — därför
    /// `try?` i stället för att låta hela översikten falla på det.
    static func fetchRecurringAllowance(memberId: String) async -> RecurringAllowanceResponseDTO? {
        let response = try? await ApiClient.shared.send(
            RecurringAllowanceResponseDTO.self,
            path: "wallet/members/\(memberId)/recurring-allowance",
            method: "GET"
        )
        return response?.active == true ? response : nil
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


// MARK: - Adult dashboard

/// Allt föräldravyn behöver, i ett anrop.
///
/// Ligger här och inte i vyn av samma skäl som `ChildDashboardRepository`: skärmen ska
/// inte veta att dagens sysslor, djuret och veckopengen är tre olika endpoints. Att
/// samla dem här gör det också möjligt att mata skärmen med `Overview`-fixtures.
enum AdultDashboardRepository {

    struct Child: Identifiable, Equatable {
        let id: String
        let name: String
        let hasPairedDevice: Bool
        let todaysDone: Int
        let todaysTotal: Int
        /// Driver kortets porträtt och dess färg. Nil tills ett ägg har valts.
        let petType: String?
        let growthStage: Int
        /// "50 kr varje fredag", eller nil när ingen automatisk peng är igång.
        let allowanceNote: String?
        /// Sant när barnets rad inte gick att läsa; kortet säger det i stället för
        /// att visa noll sysslor som om det vore ett svar.
        let loadFailed: Bool
    }

    struct Adult: Identifiable, Equatable {
        let id: String
        let name: String
        let role: String
        let hasPairedDevice: Bool
        let isCurrentUser: Bool
    }

    struct Overview: Equatable {
        let familyName: String
        let children: [Child]
        let adults: [Adult]

        var doneToday: Int { children.reduce(0) { $0 + $1.todaysDone } }
        var totalToday: Int { children.reduce(0) { $0 + $1.todaysTotal } }
    }

    static let defaultFamilyName = "Min familj"

    static func fetchOverview() async throws -> Overview {
        let members = try await FamilyRepository.fetchAllMembers()
        let childMembers = members.filter { $0.role == "CHILD" || $0.role == "ASSISTANT" }
        let adultMembers = members.filter { $0.role != "CHILD" && $0.role != "ASSISTANT" }
        let currentMemberId = TokenStoreIOS.shared.getSession()?.memberId

        let summaries = await withTaskGroup(of: Child.self) { group -> [String: Child] in
            for member in childMembers {
                group.addTask { await summary(for: member) }
            }
            var byId: [String: Child] = [:]
            for await child in group { byId[child.id] = child }
            return byId
        }

        // Serverns ordning är familjens ordning. Task-gruppen returnerar i den ordning
        // svaren kommer in, så listan sätts ihop från medlemslistan igen.
        let children = childMembers.compactMap { summaries[$0.id] }

        let adults = adultMembers.map { member in
            Adult(
                id: member.id,
                name: member.name,
                role: member.role,
                hasPairedDevice: isPaired(member),
                isCurrentUser: member.id == currentMemberId
            )
        }

        // Namnet hämtas efter medlemmarna och får misslyckas för sig: en familj utan
        // namn ska se sina barn ändå.
        let familyId = TokenStoreIOS.shared.getSession()?.familyId
            ?? members.compactMap { $0.familyId }.first
        var familyName = defaultFamilyName
        if let familyId, let fetched = try? await FamilyRepository.fetchFamilyName(familyId: familyId) {
            familyName = fetched
        }

        return Overview(familyName: familyName, children: children, adults: adults)
    }

    private static func summary(for member: FamilyMemberResponseDTO) async -> Child {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let today = formatter.string(from: Date())

        // Var och en får misslyckas för sig. Ett barn utan djur, utan peng eller utan
        // sysslor är tre vanliga tillstånd, inte tre fel.
        async let choresTask: [DailyChoreWithCompletionResponseDTO]? =
            try? DailyChoreRepositoryIOS.fetchChoresForDate(memberId: member.id, date: today)
        async let petTask: PetResponseDTO? =
            try? PetRepository.fetchPetForMember(memberId: member.id)
        async let allowanceTask: RecurringAllowanceResponseDTO? =
            WalletRepository.fetchRecurringAllowance(memberId: member.id)

        let chores = await choresTask
        let pet = await petTask
        let allowance = await allowanceTask

        return Child(
            id: member.id,
            name: member.name,
            hasPairedDevice: isPaired(member),
            todaysDone: chores?.filter { $0.completed }.count ?? 0,
            todaysTotal: chores?.count ?? 0,
            petType: pet?.petType,
            growthStage: pet?.growthStage ?? 1,
            allowanceNote: allowance.map(describeAllowance),
            loadFailed: chores == nil
        )
    }

    private static func isPaired(_ member: FamilyMemberResponseDTO) -> Bool {
        if let flag = member.hasPairedDevice { return flag }
        // Äldre svar bär inte flaggan. Token finns bara med för den som får se den,
        // så dess närvaro räcker som svar.
        return !(member.deviceToken?.isEmpty ?? true)
    }

    /// Den stående överenskommelsen på en rad, från förälderns sida av den.
    ///
    /// Nivåpengen nämner medvetet ingen summa: beloppet avgörs inte förrän månaden är
    /// slut, och en siffra här skulle läsas som ett löfte.
    private static func describeAllowance(_ schedule: RecurringAllowanceResponseDTO) -> String {
        let day = schedule.dayOfMonth ?? 1
        let ordinal = (1...2).contains(day % 10) && day != 11 && day != 12 ? "\(day):a" : "\(day):e"
        switch schedule.kind {
        case "WEEKLY":
            let weekdays = ["måndag", "tisdag", "onsdag", "torsdag", "fredag", "lördag", "söndag"]
            let index = (schedule.weekday ?? 7) - 1
            let weekday = weekdays.indices.contains(index) ? weekdays[index] : "söndag"
            return "\(schedule.amount ?? 0) kr varje \(weekday)"
        case "MONTHLY":
            return "\(schedule.amount ?? 0) kr den \(ordinal)"
        default:
            return "Efter nivå den \(ordinal)"
        }
    }
}
