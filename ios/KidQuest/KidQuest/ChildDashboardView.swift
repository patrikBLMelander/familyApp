import SwiftUI

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

    private let cardTextPrimary = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let cardTextSecondary = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)

    var body: some View {
        let petType = summary?.pet?.petType

        ZStack {
            backgroundGradient(for: petType)
                .ignoresSafeArea()
            if isLoading {
                ProgressView()
            } else if let error {
                VStack(spacing: 12) {
                    Text(error)
                        .foregroundColor(.red)
                    Button("Försök igen") {
                        Task { await load() }
                    }
                }
            } else if let summary {
                ScrollView {
                    VStack(spacing: 16) {
                        header
                        petCard(summary: summary)
                        foodCard(summary: summary)
                        tasksCard(summary: summary)
                        walletCard(summary: summary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .task {
            await load()
        }
        .sheet(isPresented: $showSelectEgg) {
            SelectEggSheet(
                onDismiss: { showSelectEgg = false },
                onEggSelected: { pet in
                    if var s = summary {
                        s = ChildDashboardRepository.Summary(
                            pet: pet,
                            xp: s.xp,
                            wallet: s.wallet,
                            collectedFood: s.collectedFood,
                            todaysTasks: s.todaysTasks
                        )
                        summary = s
                    }
                }
            )
        }
        .sheet(isPresented: $showAddChore) {
            AddChoreSheet(childId: childId, onDismiss: { showAddChore = false }, onSuccess: {
                showAddChore = false
                Task { await load() }
            })
        }
    }

    private func backgroundGradient(for petType: String?) -> LinearGradient {
        switch petType?.lowercased() {
        case "dragon":
            return LinearGradient(
                colors: [Color(red: 0x4C/255, green: 0x1D/255, blue: 0x95/255),
                         Color(red: 0x1E/255, green: 0x29/255, blue: 0x3B/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "cat":
            return LinearGradient(
                colors: [Color(red: 0xFD/255, green: 0xE6/255, blue: 0x8A/255),
                         Color(red: 0xF9/255, green: 0x73/255, blue: 0x16/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "dog":
            return LinearGradient(
                colors: [Color(red: 0xBB/255, green: 0xF7/255, blue: 0xD0/255),
                         Color(red: 0x22/255, green: 0xC5/255, blue: 0x5E/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "bird":
            return LinearGradient(
                colors: [Color(red: 0xBF/255, green: 0xDB/255, blue: 0xFE/255),
                         Color(red: 0x25/255, green: 0x63/255, blue: 0xEB/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "rabbit":
            return LinearGradient(
                colors: [Color(red: 0xFC/255, green: 0xE7/255, blue: 0xF3/255),
                         Color(red: 0xEC/255, green: 0x48/255, blue: 0x99/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "bear":
            return LinearGradient(
                colors: [Color(red: 0xFE/255, green: 0xF3/255, blue: 0xC7/255),
                         Color(red: 0x92/255, green: 0x40/255, blue: 0x0E/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "snake":
            return LinearGradient(
                colors: [Color(red: 0xDC/255, green: 0xFC/255, blue: 0xE7/255),
                         Color(red: 0x15/255, green: 0x80/255, blue: 0x3D/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "panda":
            return LinearGradient(
                colors: [Color(red: 0xE5/255, green: 0xE7/255, blue: 0xEB/255),
                         Color(red: 0x11/255, green: 0x18/255, blue: 0x27/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "slot":
            return LinearGradient(
                colors: [Color(red: 0xE5/255, green: 0xE7/255, blue: 0xEB/255),
                         Color(red: 0x6B/255, green: 0x72/255, blue: 0x80/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "hydra":
            return LinearGradient(
                colors: [Color(red: 0xC4/255, green: 0xB5/255, blue: 0xFD/255),
                         Color(red: 0x4C/255, green: 0x1D/255, blue: 0x95/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "unicorn":
            return LinearGradient(
                colors: [Color(red: 0xFD/255, green: 0xE6/255, blue: 0x8A/255),
                         Color(red: 0xF9/255, green: 0xA8/255, blue: 0xD4/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        case "kapybara":
            return LinearGradient(
                colors: [Color(red: 0xDC/255, green: 0xFC/255, blue: 0xE7/255),
                         Color(red: 0x22/255, green: 0xC5/255, blue: 0x5E/255)],
                startPoint: .top,
                endPoint: .bottom
            )
        default:
            return LinearGradient(
                colors: [Color(red: 224 / 255, green: 231 / 255, blue: 1.0),
                         Color(red: 224 / 255, green: 242 / 255, blue: 1.0)],
                startPoint: .top,
                endPoint: .bottom
            )
        }
    }

    private var header: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.backward")
                Text("Logga ut")
            }
            .foregroundColor(.white)

            Spacer()

            Text(childName)
                .font(.title2.weight(.bold))
                .foregroundColor(.white)
        }
        .padding(.top, 16)
    }

    private func petCard(summary: ChildDashboardRepository.Summary) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if let pet = summary.pet {
                let petName = pet.name ?? PetNameUtilsIOS.getPetNameSwedish(pet.petType)
                let xp = summary.xp
                let xpThresholds = [0, 10, 35, 70, 125]
                let level = xp?.currentLevel ?? 1
                let safeLevel = max(1, min(xpThresholds.count - 1, level))
                let currentThreshold = xpThresholds[safeLevel - 1]
                let nextThreshold = xpThresholds[min(safeLevel, xpThresholds.count - 1)]
                let range = max(1, nextThreshold - currentThreshold)
                let percentage: CGFloat = xp != nil
                    ? CGFloat(min(max(0, xp!.xpInCurrentLevel), range)) / CGFloat(range)
                    : 0

                let isHungry = !hasFedToday
                let moodEmoji = isHungry ? "🥺" : "😊"
                let moodText = isHungry
                    ? "Jag är hungrig... kan du ge mig mat?"
                    : "Mmm! Tack för maten idag, \(childName)! Du är bäst! 🥰"

                // Pratbubbla
                Text(moodText)
                    .font(.body)
                    .foregroundColor(cardTextPrimary)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(isHungry ? Color(red: 1.0, green: 228/255, blue: 214/255)
                                           : Color(red: 209/255, green: 250/255, blue: 229/255))
                    )
                    .frame(maxWidth: .infinity, alignment: .center)

                // Bild + XP-cirkel
                VStack(spacing: 8) {
                    PetVisual(petType: pet.petType, growthStage: pet.growthStage)
                        .frame(maxWidth: .infinity)
                        .frame(height: 220)

                    ZStack {
                        Circle()
                            .stroke(Color.gray.opacity(0.3), lineWidth: 6)
                            .frame(width: 96, height: 96)

                        Circle()
                            .trim(from: 0, to: percentage)
                            .stroke(Color.purple, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .frame(width: 96, height: 96)

                        VStack {
                            Text(moodEmoji)
                                .font(.title2)
                            Text("\(level)")
                                .font(.footnote)
                                .foregroundColor(cardTextPrimary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }

                Text(petName)
                    .font(.title3.weight(.bold))
                    .foregroundColor(cardTextPrimary)
            } else {
                Text("Inget djur denna månad")
                    .font(.body.weight(.semibold))
                    .foregroundColor(cardTextPrimary)
                Text("Välj ett ägg för att komma igång.")
                    .font(.subheadline)
                    .foregroundColor(cardTextSecondary)
                Button("Välj ägg") {
                    showSelectEgg = true
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.75))
        )
    }

    private func foodCard(summary: ChildDashboardRepository.Summary) -> some View {
        let totalFood = summary.collectedFood?.totalCount ?? 0
        let petType = summary.pet?.petType
        let foodEmoji = PetFoodUtilsIOS.emoji(for: petType)
        let foodName = PetFoodUtilsIOS.name(for: petType)

        return VStack(alignment: .leading, spacing: 12) {
            Text("Mat att ge")
                .font(.headline)
                .foregroundColor(cardTextPrimary)

            Text("Du har \(totalFood) \(foodName) att ge.")
                .font(.subheadline)
                .foregroundColor(cardTextSecondary)

            HStack(spacing: 8) {
                Button {
                    Task { await feed(amount: 1) }
                } label: {
                    Text("Mata 1")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.yellow)
                .disabled(totalFood < 1 || isFeeding)

                Button {
                    Task { await feed(amount: totalFood) }
                } label: {
                    Text("Mata allt (\(totalFood))")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.orange)
                .disabled(totalFood == 0 || isFeeding)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.75))
        )
    }

    private func tasksCard(summary: ChildDashboardRepository.Summary) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Dagens uppgifter")
                    .font(.headline)
                    .foregroundColor(cardTextPrimary)
                Spacer()
                Button("+ Lägg till") {
                    showAddChore = true
                }
                .font(.subheadline)
            }

            if summary.todaysTasks.isEmpty {
                Text("Inga uppgifter idag.")
                    .font(.subheadline)
                    .foregroundColor(cardTextSecondary)
            } else {
                ForEach(summary.todaysTasks, id: \.chore.id) { task in
                    Button {
                        Task { await toggleTask(task) }
                    } label: {
                        HStack {
                            Image(systemName: task.completed ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(task.completed ? .green : .gray)
                            VStack(alignment: .leading) {
                                Text(task.chore.title)
                                    .foregroundColor(task.completed ? cardTextSecondary : cardTextPrimary)
                                    .strikethrough(task.completed)
                                if task.chore.xpPoints > 0 {
                                    Text("\(task.chore.xpPoints) mat")
                                        .font(.caption)
                                        .foregroundColor(cardTextSecondary)
                                }
                            }
                            Spacer()
                        }
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.75))
        )
    }

    private func walletCard(summary: ChildDashboardRepository.Summary) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Plånbok")
                .font(.headline)
                .foregroundColor(cardTextPrimary)

            if let wallet = summary.wallet {
                Text("Saldo: \(wallet.balance) kr")
                    .foregroundColor(cardTextPrimary)
            } else {
                Text("Saldo okänt.")
                    .foregroundColor(cardTextSecondary)
            }

            Button {
                onOpenWallet()
            } label: {
                Text("Öppna plånbok")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.white.opacity(0.75))
        )
    }

    private func toggleTask(_ task: DailyChoreWithCompletionResponseDTO) async {
        guard let s = summary else { return }
        // Optimistic update – flip the checkbox immediately
        let updatedTasks = s.todaysTasks.map { t in
            t.chore.id == task.chore.id
                ? DailyChoreWithCompletionResponseDTO(chore: t.chore, completed: !t.completed, completionId: t.completionId)
                : t
        }
        summary = ChildDashboardRepository.Summary(
            pet: s.pet, xp: s.xp, wallet: s.wallet,
            collectedFood: s.collectedFood, todaysTasks: updatedTasks
        )
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        let dateStr = formatter.string(from: Date())
        do {
            try await DailyChoreRepositoryIOS.toggleChoreCompletion(
                choreId: task.chore.id,
                date: dateStr,
                isCompleted: task.completed
            )
            // Reload so collectedFood (and XP) reflect the change, without showing spinner
            await load(showLoadingSpinner: false)
        } catch {
            // Revert on failure
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
        // Optimistic: reduce food count immediately
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
            // Revert on failure
            summary = s
            hasFedToday = false
        }
        isFeeding = false
    }
}

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

