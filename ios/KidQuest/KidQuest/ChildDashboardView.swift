import SwiftUI

/// Barnets vy: "Dagens lista".
///
/// Den enda skärm barnen ser, och den enda som aldrig fått en omgång -- föräldrasidan
/// fick årstidstema, mörkt läge och fyra nya vyer medan den här stod still.
///
/// Ordningen är vald efter vilken fråga ett barn öppnar appen med på en tisdagmorgon:
/// **vad ska jag göra nu?** Därför är listan störst och ligger på vitt så den går att
/// läsa, och djuret bor i ett band ovanför. Bandet är kort så länge det finns uppgifter
/// kvar och växer när dagen är klar -- belöningen kommer efter arbetet, inte före.
///
/// Mörkt läge används inte här med flit. Det är föräldrarnas inställning, för det är de
/// som sitter i appen på kvällen; barnets skärm ska vara ljus och ha en årstid i den.
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
    @State private var hasFedToday: Bool = false
    @State private var showAddChore: Bool = false

    /// Icke-nil renderar de här värdena i stället för att anropa nätet. Bara [fixture]
    /// sätter den; den ligger utanför #if DEBUG så att typen har samma form i båda
    /// konfigurationerna.
    var preloaded: ChildDashboardRepository.Summary?
    var preloadedHistory: [PetHistoryResponseDTO] = []
    /// Startar i samlingens läsläge. Bara fixturen sätter den; annars nås läget genom
    /// att trycka på ett djur, vilket simulatorn inte kan.
    var preloadedViewingPast: PetHistoryResponseDTO?

    /// Djuren barnet haft tidigare. Tom lista är det normala under första månaden.
    @State private var history: [PetHistoryResponseDTO] = []
    /// Nil betyder dagens djur. Sätts när barnet trycker på ett tidigare djur i raden,
    /// och då är bandet en utställning: ingen mat, ingen humörreplik.
    @State private var viewingPast: PetHistoryResponseDTO?

    /// Alltid den ljusa paletten för årstiden -- se typkommentaren.
    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    private var tasks: [DailyChoreWithCompletionResponseDTO] { summary?.todaysTasks ?? [] }
    private var doneCount: Int { tasks.filter(\.completed).count }
    private var allDone: Bool { !tasks.isEmpty && doneCount == tasks.count }
    private var totalFood: Int { summary?.collectedFood?.totalCount ?? 0 }

    /// Bandet är kort med uppgifter kvar och stort när de är slut. Måtten är valda så
    /// att listan alltid börjar ovanför skärmens mitt i det korta läget.
    private var bandHeight: CGFloat { allDone ? 400 : 258 }

    var body: some View {
        ZStack {
            palette.pageBg.ignoresSafeArea()

            if isLoading {
                ProgressView().tint(palette.accent)
            } else if let error {
                errorState(error)
            } else if summary != nil {
                content
            }
        }
        .environment(\.seasonPalette, palette)
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

    private var content: some View {
        ScrollView {
            VStack(spacing: 0) {
                band
                VStack(spacing: 14) {
                    if viewingPast == nil {
                        tasksCard
                        feedButton
                    } else {
                        backToNowCard
                    }
                    signOutRow
                }
                .padding(.horizontal, 14)
                .padding(.top, 14)
                .padding(.bottom, 28)
            }
        }
        .ignoresSafeArea(edges: .top)
    }

    // MARK: - Bandet

    private var band: some View {
        let shown = shownPet
        return ZStack(alignment: .bottomLeading) {
            if let shown {
                PetVisual(
                    petType: shown.type,
                    growthStage: shown.stage,
                    cornerRadius: 0,
                    // Djuret kryper åt sidan när det finns arbete kvar och kliver fram
                    // i mitten när dagen är klar.
                    scale: allDone && viewingPast == nil ? 0.82 : 0.52,
                    alignment: allDone && viewingPast == nil ? .bottom : .bottomTrailing
                )
                .frame(height: bandHeight)
                .clipped()
            } else {
                palette.headerTop.frame(height: bandHeight)
            }

            // Läsbarhet: bandets nedre kant tonar in i sidans färg, och toppen mörknar
            // så att den vita raden med knappar syns mot vilken årstid som helst.
            LinearGradient(
                colors: [
                    .black.opacity(0.34), .clear, .clear,
                    palette.pageBg.opacity(0.55), palette.pageBg,
                ],
                startPoint: .top, endPoint: .bottom
            )
            .frame(height: bandHeight)
            .allowsHitTesting(false)

            bandLabels
        }
        .frame(height: bandHeight)
        .overlay(alignment: .top) { bandTopRow }
        .animation(.easeInOut(duration: 0.45), value: allDone)
    }

    private var bandTopRow: some View {
        HStack(alignment: .top, spacing: 8) {
            petSwitcher
            Spacer(minLength: 8)
            walletChip
        }
        .padding(.horizontal, 14)
        .padding(.top, 52)
    }

    private var bandLabels: some View {
        VStack(alignment: .leading, spacing: 7) {
            if let past = viewingPast {
                Text(monthLabel(year: past.year, month: past.month).uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.1)
                    .foregroundStyle(.white.opacity(0.9))
                Text(PetNameUtilsIOS.getPetNameSwedish(past.petType))
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
            } else if let pet = summary?.pet {
                Text("\(petDisplayName(pet)) · NIVÅ \(level)".uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.1)
                    .foregroundStyle(.white.opacity(0.92))
                if allDone {
                    Text("Allt klart idag!")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.white)
                }
                if totalFood > 0 {
                    foodChip
                }
            }
        }
        .shadow(color: .black.opacity(0.45), radius: 5, y: 1)
        .padding(.horizontal, 18)
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var foodChip: some View {
        HStack(spacing: 5) {
            Text(PetFoodUtilsIOS.emoji(for: shownPet?.type))
                .font(.system(size: 13))
            Text("\(totalFood) mat att ge")
                .font(.caption.weight(.bold))
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .background(Capsule().fill(.white.opacity(0.94)))
        .foregroundStyle(palette.tipStrong)
        .shadow(color: .clear, radius: 0)
    }

    /// Plånbokens saldo, och vägen dit. Ett tryck och inte ett kort: bandet ska inte
    /// konkurrera med listan om uppmärksamheten.
    private var walletChip: some View {
        Button(action: onOpenWallet) {
            HStack(spacing: 5) {
                Image(systemName: "wallet.bifold.fill")
                    .font(.system(size: 12, weight: .semibold))
                Text(summary?.wallet.map { "\($0.balance) kr" } ?? "Plånbok")
                    .font(.caption.weight(.bold))
                Image(systemName: "chevron.right")
                    .font(.system(size: 9, weight: .bold))
                    .opacity(0.6)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 7)
            .background(Capsule().fill(.white.opacity(0.92)))
            .foregroundStyle(palette.accent)
        }
        .buttonStyle(.plain)
    }

    /// Raden med insamlade djur. Dagens först, sedan tidigare månader.
    ///
    /// Visas bara när det finns något att växla mellan -- en enda cirkel att trycka på
    /// är ingen växling, bara en prick.
    @ViewBuilder
    private var petSwitcher: some View {
        if !history.isEmpty, let current = summary?.pet {
            HStack(spacing: 6) {
                petCircle(
                    type: current.petType,
                    stage: current.growthStage,
                    active: viewingPast == nil
                ) { viewingPast = nil }

                ForEach(history.prefix(5)) { past in
                    petCircle(
                        type: past.petType,
                        stage: past.finalGrowthStage,
                        active: viewingPast?.id == past.id
                    ) { viewingPast = past }
                }
            }
        }
    }

    private func petCircle(
        type: String,
        stage: Int,
        active: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(.white.opacity(0.92))
                if let name = PetImagesIOS.petImageName(for: type, growthStage: stage) {
                    Image(name)
                        .resizable()
                        .scaledToFit()
                        .padding(2)
                }
            }
            .frame(width: 36, height: 36)
            .overlay(
                Circle().stroke(
                    active ? palette.warnStrong : .white.opacity(0.9),
                    lineWidth: active ? 2.5 : 1.5
                )
            )
            .opacity(active ? 1 : 0.72)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(PetNameUtilsIOS.getPetNameSwedish(type))
    }

    // MARK: - Uppgifterna

    private var tasksCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Dagens uppgifter")
                    .font(.system(size: 21, weight: .bold))
                    .foregroundStyle(palette.ink)
                Spacer()
                if tasks.isEmpty {
                    Button("+ Lägg till") { showAddChore = true }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.accent)
                } else {
                    Text("\(doneCount) / \(tasks.count)")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(allDone ? palette.goodInk : palette.inkSoft)
                }
            }
            .padding(.bottom, 10)

            if tasks.isEmpty {
                emptyTasks
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(tasks.enumerated()), id: \.element.chore.id) { index, task in
                        taskRow(task)
                        if index < tasks.count - 1 {
                            Divider().overlay(palette.cardEdge.opacity(0.7))
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(palette.cardEdge, lineWidth: 1)
                )

                if !tasks.isEmpty {
                    Button("+ Lägg till en syssla") { showAddChore = true }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.inkSoft)
                        .padding(.top, 10)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
        }
    }

    private func taskRow(_ task: DailyChoreWithCompletionResponseDTO) -> some View {
        Button {
            Task { await toggleTask(task) }
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(task.completed ? palette.goodInk : palette.pageBg)
                    if task.completed {
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .heavy))
                            .foregroundStyle(.white)
                    } else {
                        Circle().stroke(palette.cardEdge, lineWidth: 2)
                    }
                }
                .frame(width: 28, height: 28)

                Text(task.chore.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(task.completed ? palette.inkFaint : palette.ink)
                    .strikethrough(task.completed, color: palette.inkFaint)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if task.chore.xpPoints > 0 {
                    Text("+\(task.chore.xpPoints) mat")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(task.completed ? palette.inkFaint : palette.tipStrong)
                }
            }
            .padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var emptyTasks: some View {
        VStack(spacing: 6) {
            Text(summary?.pet == nil ? "Välj ett ägg först" : "Inga uppgifter idag")
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.ink)
            Text(summary?.pet == nil
                 ? "Då får du ett djur att ta hand om."
                 : "Njut av dagen!")
                .font(.subheadline)
                .foregroundStyle(palette.inkSoft)
            if summary?.pet == nil {
                Button("Välj ägg") { showSelectEgg = true }
                    .font(.body.weight(.bold))
                    .foregroundStyle(palette.onAccent)
                    .padding(.horizontal, 22).padding(.vertical, 12)
                    .background(Capsule().fill(palette.accent))
                    .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 26)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
        )
    }

    // MARK: - Matningen

    /// Ligger i flödet under listan och inte fastlåst längst ner. Samma beslut som i
    /// föräldravyn, av samma skäl: en knapp som alltid ligger över innehållet gör
    /// skärmen trängre än den behöver vara.
    @ViewBuilder
    private var feedButton: some View {
        if summary?.pet != nil {
            let name = summary?.pet.map(petDisplayName) ?? "djuret"
            VStack(spacing: 8) {
                Button {
                    Task { await feed(amount: totalFood) }
                } label: {
                    Text(totalFood > 0
                         ? "Mata \(name) med \(totalFood) mat"
                         : "Ingen mat att ge än")
                        .font(.system(size: 17, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .foregroundStyle(totalFood > 0 ? palette.onAccent : palette.inkFaint)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(totalFood > 0 ? palette.accent : palette.outlineBg)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(totalFood > 0 ? .clear : palette.outlineEdge, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(totalFood == 0 || isFeeding)

                if totalFood > 1 {
                    Button("Mata bara 1") { Task { await feed(amount: 1) } }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.inkSoft)
                        .disabled(isFeeding)
                }
            }
        }
    }

    // MARK: - Övrigt

    private var backToNowCard: some View {
        VStack(spacing: 10) {
            Text("Det här djuret är färdigväxt")
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.ink)
            Text("Du kan inte mata det längre, men det stannar i din samling.")
                .font(.subheadline)
                .foregroundStyle(palette.inkSoft)
                .multilineTextAlignment(.center)
            Button("Tillbaka till nu") { viewingPast = nil }
                .font(.body.weight(.bold))
                .foregroundStyle(palette.onAccent)
                .padding(.horizontal, 22).padding(.vertical, 12)
                .background(Capsule().fill(palette.accent))
        }
        .frame(maxWidth: .infinity)
        .padding(22)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
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

    // MARK: - Härledda värden

    private var shownPet: (type: String, stage: Int)? {
        if let past = viewingPast { return (past.petType, past.finalGrowthStage) }
        if let pet = summary?.pet { return (pet.petType, pet.growthStage) }
        return nil
    }

    /// Samma trappa som förut: nivå 1 börjar på 0 mat, nivå 5 på 125.
    private var level: Int {
        let thresholds = [0, 10, 35, 70, 125]
        return max(1, min(thresholds.count, summary?.xp?.currentLevel ?? 1))
    }

    private func petDisplayName(_ pet: PetResponseDTO) -> String {
        let given = pet.name?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let given, !given.isEmpty { return given }
        return PetNameUtilsIOS.getPetNameSwedish(pet.petType)
    }

    private func monthLabel(year: Int, month: Int) -> String {
        let names = ["januari", "februari", "mars", "april", "maj", "juni",
                     "juli", "augusti", "september", "oktober", "november", "december"]
        let name = names[max(0, min(11, month - 1))]
        return "\(name) \(year)"
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
            }
        } catch {
            await MainActor.run {
                self.error = "Kunde inte ladda barnvyn."
                isLoading = false
            }
        }
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
    static func fixture(allDone: Bool = false, past: Bool = false) -> ChildDashboardView {
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

        let tasks = [
            chore("1", "Bädda sängen", 1, true),
            chore("2", "Packa skolväskan", 1, true),
            chore("3", "Plocka undan efter mellis", 1, allDone),
            chore("4", "Kvällsrutin utan tjat", 2, allDone),
            chore("5", "Hjälpa till med disken", 1, allDone),
        ]

        let history = [
            PetHistoryResponseDTO(
                id: "h1", memberId: "child-1", year: 2026, month: 8,
                selectedEggType: "blue_egg", petType: "dragon", finalGrowthStage: 5
            ),
            PetHistoryResponseDTO(
                id: "h2", memberId: "child-1", year: 2026, month: 7,
                selectedEggType: "pink_egg", petType: "unicorn", finalGrowthStage: 4
            ),
        ]

        return ChildDashboardView(
            childId: "child-1",
            childName: "Signe",
            preloaded: ChildDashboardRepository.Summary(
                pet: PetResponseDTO(
                    id: "p1", memberId: "child-1", year: 2026, month: 9,
                    selectedEggType: "yellow_egg", petType: "bird", name: "Kvitter",
                    growthStage: 4, hatchedAt: nil,
                    createdAt: "2026-09-01T08:00:00Z", updatedAt: "2026-09-01T08:00:00Z"
                ),
                xp: XpProgressResponseDTO(
                    id: "x1", memberId: "child-1", year: 2026, month: 9,
                    currentXp: 42, currentLevel: 3, totalTasksCompleted: 28, xpForNextLevel: 70, xpInCurrentLevel: 7
                ),
                wallet: WalletBalanceResponseDTO(id: "w1", memberId: "child-1", balance: 85),
                collectedFood: CollectedFoodResponseDTO(foodItems: [], totalCount: allDone ? 5 : 2),
                todaysTasks: tasks
            ),
            preloadedHistory: history,
            preloadedViewingPast: past ? history.first : nil
        )
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

