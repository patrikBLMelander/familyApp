import SwiftUI

/// Barnets egen skärm, på barnets egen telefon.
///
/// Utseendet ligger i [ChildDayLayout], som föräldrarnas "Visa som barn" också ritar sig
/// ur. Det som finns här är bara data: varje läsning och skrivning går genom barnets
/// egen device-token, som servern översätter till medlemmen. Värden gör motsatsen och
/// namnger barnet i sökvägen -- se ChildDashboardHost för varför de två är skilda.
struct ChildDashboardView: View {
    let childId: String
    let childName: String
    var onBack: () -> Void = {}
    var onOpenTasks: () -> Void = {}
    var onOpenWallet: () -> Void = {}

    @State private var isLoading: Bool = true
    @State private var error: String?
    @State private var summary: ChildDashboardRepository.Summary?
    @State private var isFeeding: Bool = false
    @State private var showSelectEgg: Bool = false
    @State private var hasAutoOpenedEgg: Bool = false
    @State private var hasFedToday: Bool = false
    @State private var showAddChore: Bool = false
    @State private var history: [PetHistoryResponseDTO] = []
    @State private var viewingPast: PetHistoryResponseDTO?

    /// Icke-nil renderar de här värdena i stället för att anropa nätet. Bara [fixture]
    /// sätter dem; de ligger utanför #if DEBUG så att typen har samma form i båda
    /// konfigurationerna.
    var preloaded: ChildDashboardRepository.Summary?
    var preloadedHistory: [PetHistoryResponseDTO] = []
    var preloadedViewingPast: PetHistoryResponseDTO?
    /// Bara harnesket sätter den; se ChildDayLayout.harnessAutoFeed.
    var harnessAutoFeed: Bool = false

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    var body: some View {
        ZStack {
            palette.pageBg.ignoresSafeArea()

            if isLoading {
                ProgressView().tint(palette.accent)
            } else if let error {
                errorState(error)
            } else if let summary {
                layout(summary)
            }
        }
        .task {
            if let preloaded {
                summary = preloaded
                history = preloadedHistory
                viewingPast = preloadedViewingPast
                isLoading = false
                return
            }
            await load()
            history = await ChildDashboardRepository.fetchPetHistory()
        }
        .sheet(isPresented: $showSelectEgg) {
            SelectEggSheet(
                onDismiss: { showSelectEgg = false },
                onEggSelected: { pet in
                    if let s = summary {
                        summary = ChildDashboardRepository.Summary(
                            pet: pet, xp: s.xp, wallet: s.wallet,
                            collectedFood: s.collectedFood, todaysTasks: s.todaysTasks
                        )
                    }
                }
            )
        }
        .sheet(isPresented: $showAddChore) {
            AddChoreSheet(childId: childId, onDismiss: { showAddChore = false }, onSuccess: {
                showAddChore = false
                Task { await load(showLoadingSpinner: false) }
            })
        }
    }

    private func layout(_ s: ChildDashboardRepository.Summary) -> some View {
        ChildDayLayout(
            childName: childName,
            pet: s.pet,
            level: max(1, min(5, s.xp?.currentLevel ?? 1)),
            xpInLevel: s.xp?.xpInCurrentLevel ?? 0,
            xpForNext: s.xp?.xpForNextLevel ?? 0,
            foodCount: s.collectedFood?.totalCount ?? 0,
            balance: s.wallet?.balance,
            tasks: s.todaysTasks,
            history: history,
            viewingPast: $viewingPast,
            isFeeding: isFeeding,
            onToggleTask: { task in Task { await toggleTask(task) } },
            onFeed: { amount in Task { await feed(amount: amount) } },
            onOpenWallet: onOpenWallet,
            onSelectEgg: { showSelectEgg = true },
            onAddChore: { showAddChore = true },
            harnessAutoFeed: harnessAutoFeed,
            banner: { EmptyView() },
            footer: { signOutRow }
        )
    }

    /// Långt ner och litet. Det är vägen ut, inte något ett barn ska trycka på av
    /// misstag mitt i sin lista.
    private var signOutRow: some View {
        Button(action: onBack) {
            Text("Logga ut \(childName)")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.inkFaint)
        }
        .buttonStyle(.plain)
        .padding(.top, 12)
    }

    private func errorState(_ message: String) -> some View {
        VStack(spacing: 12) {
            Text(message).foregroundStyle(palette.danger)
            Button("Försök igen") { Task { await load() } }
                .font(.body.weight(.bold))
                .foregroundStyle(palette.onAccent)
                .padding(.horizontal, 22).padding(.vertical, 12)
                .background(Capsule().fill(palette.accent))
        }
        .padding(24)
    }

    // MARK: - Anrop

    private func toggleTask(_ task: DailyChoreWithCompletionResponseDTO) async {
        guard let s = summary else { return }
        // Optimistiskt: bocken vänder direkt, innan servern svarat.
        let updated = s.todaysTasks.map { t in
            t.chore.id == task.chore.id
                ? DailyChoreWithCompletionResponseDTO(
                    chore: t.chore, completed: !t.completed, completionId: t.completionId)
                : t
        }
        summary = ChildDashboardRepository.Summary(
            pet: s.pet, xp: s.xp, wallet: s.wallet,
            collectedFood: s.collectedFood, todaysTasks: updated
        )
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        do {
            try await DailyChoreRepositoryIOS.toggleChoreCompletion(
                choreId: task.chore.id,
                date: formatter.string(from: Date()),
                isCompleted: task.completed
            )
            // Hämtar om så att maten och XP:n följer med, utan snurra.
            await load(showLoadingSpinner: false)
        } catch {
            summary = s
        }
    }

    private func load(showLoadingSpinner: Bool = true) async {
        if showLoadingSpinner { isLoading = true }
        error = nil
        do {
            let s = try await ChildDashboardRepository.fetchSummaryForCurrentMember(childId: childId)
            await MainActor.run {
                summary = s
                isLoading = false
                autoOpenEggIfNeeded(hasPet: s.pet != nil, failed: false)
            }
        } catch {
            await MainActor.run {
                self.error = "Kunde inte ladda barnvyn."
                isLoading = false
            }
        }
    }


    /// Öppnar äggväljaren en gång när det saknas djur.
    ///
    /// Android har gjort det hela tiden och iOS inte alls, vilket är varför ett barn utan
    /// djur kunde bli stående här. En gång per session med flit: dialogen ska hjälpa, inte
    /// hålla någon fast. Att stänga den är inte längre en återvändsgränd -- remsan under
    /// bandet leder tillbaka.
    private func autoOpenEggIfNeeded(hasPet: Bool, failed: Bool) {
        guard !hasAutoOpenedEgg, !hasPet, !failed else { return }
        hasAutoOpenedEgg = true
        showSelectEgg = true
    }

    private func feed(amount: Int) async {
        guard amount > 0, let s = summary else { return }
        isFeeding = true
        // Optimistiskt: matsiffran sjunker direkt.
        let newCount = max(0, (s.collectedFood?.totalCount ?? 0) - amount)
        summary = ChildDashboardRepository.Summary(
            pet: s.pet, xp: s.xp, wallet: s.wallet,
            collectedFood: CollectedFoodResponseDTO(foodItems: [], totalCount: newCount),
            todaysTasks: s.todaysTasks
        )
        hasFedToday = true
        do {
            try await ChildDashboardRepository.feedPet(xpAmount: amount)
            await load(showLoadingSpinner: false)
        } catch {
            summary = s
            hasFedToday = false
        }
        isFeeding = false
    }
}

#if DEBUG
extension ChildDashboardView {

    /// Signe en vardag i september. Uppgifterna är hämtade ur den färdiga listan för
    /// 7-9 år, alltså samma ord ett riktigt barn möts av.
    ///
    /// @param allDone renderar läget efter sista bocken, som är det enda sättet att se
    ///   att bandet växer -- simulatorn tar inte emot tryck.
    /// @param past visar ett tidigare djur i stället, alltså samlingens läsläge.
    /// @param nearLevelUp tre XP från tröskeln med fem mat i räknaren, och matningen
    ///   startar av sig själv -- det enda sättet att se nivåhöjningen i en simulator som
    ///   inte tar emot tryck.
    /// @param noPet barnet har sysslor men inget djur -- läget varje nytt barn börjar
    ///   i, eftersom fem standardsysslor skapas när barnet läggs till. Det var just den
    ///   kombinationen som gömde "Välj ägg".
    static func fixture(
        allDone: Bool = false,
        past: Bool = false,
        nearLevelUp: Bool = false,
        noPet: Bool = false
    ) -> ChildDashboardView {
        let history = ChildFixtures.history
        return ChildDashboardView(
            childId: "child-1",
            childName: "Signe",
            preloaded: ChildDashboardRepository.Summary(
                pet: noPet ? nil : ChildFixtures.pet,
                xp: nearLevelUp ? ChildFixtures.xpNearLevelUp : ChildFixtures.xp,
                wallet: WalletBalanceResponseDTO(id: "w1", memberId: "child-1", balance: 85),
                collectedFood: CollectedFoodResponseDTO(
                    foodItems: [], totalCount: (allDone || nearLevelUp) ? 5 : 2
                ),
                todaysTasks: ChildFixtures.tasks(allDone: allDone)
            ),
            preloadedHistory: history,
            preloadedViewingPast: past ? history.first : nil,
            harnessAutoFeed: nearLevelUp
        )
    }
}

/// Delade provvärden, så att barnets skärm och föräldrarnas "Visa som barn" går att
/// jämföra sida vid sida i harnesket. Skiljer de sig ska det bero på layouten, inte på
/// att de fick olika siffror.
enum ChildFixtures {

    static let pet = PetResponseDTO(
        id: "p1", memberId: "child-1", year: 2026, month: 9,
        selectedEggType: "yellow_egg", petType: "bird", name: "Kvitter",
        // Stadiet är nivån (calculateGrowthStage mappar 1:1), så en fixtur med
        // stadie 4 och nivå 3 beskriver ett tillstånd som inte kan uppstå.
        growthStage: 3, hatchedAt: nil,
        createdAt: "2026-09-01T08:00:00Z", updatedAt: "2026-09-01T08:00:00Z"
    )

    static let xp = XpProgressResponseDTO(
        id: "x1", memberId: "child-1", year: 2026, month: 9,
        currentXp: 42, currentLevel: 3, totalTasksCompleted: 28,
        // xpForNextLevel är hur många XP som FATTAS, inte tröskeln -- servern
        // returnerar tröskel minus currentXp. Fixturen hade 70, alltså tröskeln, vilket
        // gav mätaren spannet 77 i stället för 35. Felet syntes inte förrän något
        // faktiskt läste fältet.
        xpForNextLevel: 28, xpInCurrentLevel: 7
    )

    /// Tre XP från tröskeln mellan nivå 3 och 4. Trösklarna är {0, 10, 35, 70, 125}, så
    /// 67 ligger tre steg under 70 och spannet är 35.
    static let xpNearLevelUp = XpProgressResponseDTO(
        id: "x1", memberId: "child-1", year: 2026, month: 9,
        currentXp: 67, currentLevel: 3, totalTasksCompleted: 41,
        xpForNextLevel: 3, xpInCurrentLevel: 32
    )

    static let history = [
        PetHistoryResponseDTO(
            id: "h1", memberId: "child-1", year: 2026, month: 8,
            selectedEggType: "blue_egg", petType: "dragon", finalGrowthStage: 5
        ),
        PetHistoryResponseDTO(
            id: "h2", memberId: "child-1", year: 2026, month: 7,
            selectedEggType: "pink_egg", petType: "unicorn", finalGrowthStage: 4
        ),
    ]

    static func tasks(allDone: Bool) -> [DailyChoreWithCompletionResponseDTO] {
        func chore(_ id: String, _ title: String, _ food: Int, _ done: Bool)
            -> DailyChoreWithCompletionResponseDTO {
            DailyChoreWithCompletionResponseDTO(
                chore: DailyChoreResponseDTO(
                    id: id, memberId: "child-1", title: title,
                    weekdays: ChoreWeekday.all.map(\.code), xpPoints: food, isActive: true
                ),
                completed: done,
                completionId: done ? "c-\(id)" : nil
            )
        }
        return [
            chore("1", "Bädda sängen", 1, true),
            chore("2", "Packa skolväskan", 1, true),
            chore("3", "Plocka undan efter mellis", 1, allDone),
            chore("4", "Kvällsrutin utan tjat", 2, allDone),
            chore("5", "Hjälpa till med disken", 1, allDone),
        ]
    }
}
#endif

// MARK: - Add Chore Sheet

private struct AddChoreSheet: View {
    let childId: String
    var onDismiss: () -> Void
    var onSuccess: () -> Void

    @State private var title: String = ""
    @State private var selectedWeekdays: Set<String> = []
    @State private var xpPoints: Int = 1
    @State private var isLoading: Bool = false
    @State private var error: String?

    private let allWeekdays: [(String, String)] = [
        ("MON", "M"), ("TUE", "T"), ("WED", "O"), ("THU", "T"), ("FRI", "F"), ("SAT", "L"), ("SUN", "S")
    ]

    var body: some View {
        NavigationView {
            Form {
                Section("Titel") {
                    TextField("Titel", text: $title)
                }
                Section("Veckodagar") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 8) {
                        ForEach(allWeekdays, id: \.0) { day, label in
                            Button(action: {
                                if selectedWeekdays.contains(day) {
                                    selectedWeekdays.remove(day)
                                } else {
                                    selectedWeekdays.insert(day)
                                }
                            }) {
                                Text(label)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 6)
                                    .background(selectedWeekdays.contains(day) ? Color.accentColor : Color(.systemGray5))
                                    .foregroundColor(selectedWeekdays.contains(day) ? .white : .primary)
                                    .cornerRadius(6)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)
                }
                Section("XP (mat)") {
                    Picker("XP", selection: $xpPoints) {
                        Text("x1").tag(1)
                        Text("x2").tag(2)
                        Text("x3").tag(3)
                    }
                    .pickerStyle(.segmented)
                }
                if let error {
                    Section {
                        Text(error).foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Ny återkommande syssla")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { onDismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isLoading ? "Sparar…" : "Spara") {
                        guard !title.trimmingCharacters(in: .whitespaces).isEmpty else {
                            error = "Titel krävs"; return
                        }
                        guard !selectedWeekdays.isEmpty else {
                            error = "Välj minst en veckodag"; return
                        }
                        isLoading = true
                        error = nil
                        Task {
                            do {
                                let ordered = allWeekdays.map(\.0).filter { selectedWeekdays.contains($0) }
                                try await DailyChoreRepositoryIOS.createChore(
                                    memberId: childId,
                                    title: title.trimmingCharacters(in: .whitespaces),
                                    weekdays: ordered,
                                    xpPoints: xpPoints
                                )
                                onSuccess()
                            } catch {
                                self.error = error.localizedDescription
                            }
                            isLoading = false
                        }
                    }
                    .disabled(isLoading)
                }
            }
        }
    }
}

