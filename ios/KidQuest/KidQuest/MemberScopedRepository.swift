import Foundation

/// Serverns svar på `pets/.../last-fed-date`. En ISO-tidsstämpel, eller null när
/// djuret aldrig fått mat.
///
/// Lives here rather than in ApiModels because this is the only caller: the
/// self-scoped screens never ask, they only remember that they just fed.
struct LastFedDateResponseDTO: Decodable {
    let lastFedDate: String?
}

/// The pet, XP, chore and wallet calls that NAME the child they are about.
///
/// Everything the child's own dashboard calls — `pets/current`, `xp/current`,
/// `pets/feed`, `pets/select-egg`, `pets/collected-food`, `wallet/balance` — resolves
/// the member from the device token. That is correct on a child's own phone and wrong
/// on a parent's: the same "Mata allt" button would pour the child's food into the
/// PARENT's pet, and "Välj ägg" would create a pet for the parent. The server has
/// member-scoped twins of every one of those routes, authorised as a parent in the
/// same family, and this is where they live.
///
/// It deliberately owns no code that already exists elsewhere:
/// - `PetRepository.fetchPetForMember` / `awardBonusXp` (Repositories.swift)
/// - `ParentWalletRepository.fetchBalance` / `fetchTransactions` / `recordExpense`
/// - `DailyChoreRepositoryIOS.fetchChoresForDate` — already member-scoped
/// - `DailyChoreRepositoryIOS.toggleChoreCompletion` — safe as it stands, see below
enum MemberScopedRepository {

    // MARK: - Reads

    /// XP for one child. `ChildPetView` spells this path out inline; this is the same
    /// call given a name so the child view does not spell it out a third time.
    static func fetchXpProgress(memberId: String) async throws -> XpProgressResponseDTO {
        try await ApiClient.shared.send(
            XpProgressResponseDTO.self,
            path: "xp/members/\(memberId)/current",
            method: "GET"
        )
    }

    /// The food this child has earned and not yet fed to their pet.
    static func fetchCollectedFood(memberId: String) async throws -> CollectedFoodResponseDTO {
        try await ApiClient.shared.send(
            CollectedFoodResponseDTO.self,
            path: "pets/members/\(memberId)/collected-food",
            method: "GET"
        )
    }

    /// När djuret senast fick mat, som `yyyy-MM-dd`, eller nil när det aldrig ätit.
    ///
    /// The server returns a full timestamp; only the date half decides whether the pet
    /// is hungry today, so the rest is dropped here rather than in the view.
    static func fetchLastFedDate(memberId: String) async throws -> String? {
        let response = try await ApiClient.shared.send(
            LastFedDateResponseDTO.self,
            path: "pets/members/\(memberId)/last-fed-date",
            method: "GET"
        )
        guard let raw = response.lastFedDate, raw.count >= 10 else { return nil }
        return String(raw.prefix(10))
    }

    // MARK: - Writes

    /// Ge mat till BARNETS djur, inte till den inloggades.
    static func feedPet(memberId: String, xpAmount: Int) async throws {
        let body = FeedPetRequestDTO(xpAmount: xpAmount)
        try await ApiClient.shared.sendWithoutResponse(
            path: "pets/members/\(memberId)/feed",
            method: "POST",
            body: body
        )
    }

    /// Välj månadens ägg åt barnet.
    static func selectEgg(memberId: String, eggType: String, name: String?) async throws -> PetResponseDTO {
        let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines)
        let body = SelectEggRequestDTO(
            eggType: eggType,
            name: (trimmed?.isEmpty == false) ? trimmed : nil
        )
        return try await ApiClient.shared.send(
            PetResponseDTO.self,
            path: "pets/members/\(memberId)/select-egg",
            method: "POST",
            body: body
        )
    }

    // MARK: - The whole screen in one read

    /// Everything the child's dashboard draws, for one child, from the member-scoped
    /// routes only.
    struct Snapshot {
        var pet: PetResponseDTO?
        /// Skilt från "inget djur": själva anropet gick fel, så vi får inte påstå att
        /// barnet inte valt ägg.
        ///
        /// A 404 genuinely means "no pet this month" and is what opens the egg picker.
        /// A 500 or a dropped connection must not be reported the same way, or a child
        /// who already has a pet is told to pick another and then hits "Pet already
        /// selected for this month".
        var petLoadFailed: Bool
        var xp: XpProgressResponseDTO?
        var balance: WalletBalanceResponseDTO?
        var foodCount: Int
        /// `yyyy-MM-dd`, or nil when the pet has never been fed.
        var lastFedDate: String?
        var todaysChores: [DailyChoreWithCompletionResponseDTO]
    }

    /// Läser hela barnets vy parallellt.
    ///
    /// Only the chore list is allowed to fail the whole read: a child with no pet, no
    /// wallet row yet and no XP row yet is three ordinary states, not three errors, and
    /// each one has a card that says so on its own.
    /// Djuren barnet haft tidigare. Namnger barnet i sökvägen, som allt annat här --
    /// `pets/history` hade gett förälderns egna djur.
    ///
    /// Sväljer felet: samlingen är en trevlighet och ska inte stoppa resten av skärmen.
    static func fetchPetHistory(memberId: String) async -> [PetHistoryResponseDTO] {
        let all = (try? await ApiClient.shared.send(
            [PetHistoryResponseDTO].self,
            path: "pets/members/\(memberId)/history",
            method: "GET"
        )) ?? []
        return all.sorted { ($0.year, $0.month) > ($1.year, $1.month) }
    }

    static func fetchSnapshot(memberId: String, date: Date = Date()) async throws -> Snapshot {
        let day = DailyChoreRepositoryIOS.apiDate(date)

        async let petResult = fetchPetResult(memberId: memberId)
        async let xpResult = optional { try await fetchXpProgress(memberId: memberId) }
        async let balanceResult = optional { try await ParentWalletRepository.fetchBalance(memberId: memberId) }
        async let foodResult = optional { try await fetchCollectedFood(memberId: memberId) }
        async let lastFedResult = optional { try await fetchLastFedDate(memberId: memberId) }
        async let choresResult = DailyChoreRepositoryIOS.fetchChoresForDate(memberId: memberId, date: day)

        let pet = await petResult
        let chores = try await choresResult
        let xp = await xpResult
        let balance = await balanceResult
        let food = await foodResult
        // Double optional flattened: the call itself can fail, and can also succeed
        // with "never fed". Both mean the same thing to the screen.
        let lastFed = await lastFedResult ?? nil

        return Snapshot(
            pet: pet.pet,
            petLoadFailed: pet.failed,
            xp: xp,
            balance: balance,
            foodCount: food?.totalCount ?? 0,
            lastFedDate: lastFed,
            todaysChores: chores
        )
    }

    /// Hämtar djuret och skiljer "inget djur" från "gick inte att läsa".
    private static func fetchPetResult(memberId: String) async -> (pet: PetResponseDTO?, failed: Bool) {
        do {
            let pet = try await PetRepository.fetchPetForMember(memberId: memberId)
            return (pet, false)
        } catch ApiError.httpError(404, _) {
            // Inget djur den här månaden — det är ett svar, inte ett fel.
            return (nil, false)
        } catch {
            return (nil, true)
        }
    }

    /// `try?` around an async call, in a form `async let` accepts.
    ///
    /// `async let x = try? f()` infers a throwing child task on some toolchains and
    /// then complains at the use site; wrapping the throw away first keeps every
    /// optional read here non-throwing and the call site readable.
    private static func optional<T>(_ work: @Sendable @escaping () async throws -> T) async -> T? {
        try? await work()
    }
}
