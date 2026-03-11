import SwiftUI

struct ChildWalletView: View {
    let childName: String
    let childId: String
    let isOwnWallet: Bool
    var onBack: () -> Void = {}

    @State private var balance: WalletBalanceResponseDTO?
    @State private var savingsGoals: [SavingsGoalResponseDTO] = []
    @State private var transactions: [WalletTransactionResponseDTO] = []
    @State private var categories: [ExpenseCategoryResponseDTO] = []
    @State private var petType: String?
    @State private var pendingNotification: WalletNotificationResponseDTO?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var showExpenseSheet = false
    @State private var showCreateGoalSheet = false
    @State private var showGiveMoneySheet = false
    @State private var showAllocateSheet = false

    private let cardTextPrimary = Color(red: 0x1C/255, green: 0x19/255, blue: 0x17/255)
    private let cardTextSecondary = Color(red: 0x57/255, green: 0x53/255, blue: 0x4E/255)

    var body: some View {
        ZStack {
            backgroundGradient(for: petType).ignoresSafeArea()

            if isLoading {
                ProgressView()
            } else if let msg = errorMessage {
                VStack(spacing: 12) {
                    Text(msg).foregroundColor(.red)
                    Button("Försök igen") { Task { await load() } }
                }
            } else {
                ScrollView {
                    VStack(spacing: 16) {
                        header
                        if let b = balance {
                            balanceCard(balance: b)
                        }
                        if isOwnWallet {
                            savingsGoalsSection
                        }
                        transactionsSection
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .task { await load() }
        .sheet(isPresented: $showExpenseSheet) {
            RecordExpenseSheet(
                currentBalance: balance?.balance ?? 0,
                categories: categories,
                onDismiss: { showExpenseSheet = false },
                onSuccess: { showExpenseSheet = false; Task { await load() } }
            )
        }
        .sheet(isPresented: $showCreateGoalSheet) {
            CreateSavingsGoalSheet(
                onDismiss: { showCreateGoalSheet = false },
                onSuccess: { showCreateGoalSheet = false; Task { await load() } }
            )
        }
        .sheet(isPresented: $showAllocateSheet) {
            AllocateToGoalsSheet(
                currentBalance: balance?.balance ?? 0,
                activeGoals: savingsGoals.filter { $0.isActive && !$0.isCompleted },
                onDismiss: { showAllocateSheet = false },
                onSuccess: { showAllocateSheet = false; Task { await load() } }
            )
        }
        .sheet(isPresented: $showGiveMoneySheet) {
            GiveMoneySheet(
                childName: childName,
                childId: childId,
                onDismiss: { showGiveMoneySheet = false },
                onSuccess: { showGiveMoneySheet = false; Task { await load() } }
            )
        }
        .overlay {
            if let notification = pendingNotification {
                AllowanceNotificationOverlay(notification: notification) {
                    Task {
                        try? await WalletRepository.markNotificationShown(notificationId: notification.id)
                        pendingNotification = nil
                    }
                }
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.backward")
                Text("Tillbaka")
            }
            .foregroundColor(.white)
            Spacer()
            Text("\(childName) – Plånbok")
                .font(.title2.weight(.bold))
                .foregroundColor(.white)
        }
        .padding(.top, 16)
    }

    // MARK: - Balance card

    private func balanceCard(balance: WalletBalanceResponseDTO) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Saldo")
                .font(.headline)
                .foregroundColor(cardTextSecondary)
            Text("\(balance.balance) kr")
                .font(.largeTitle.weight(.bold))
                .foregroundColor(cardTextPrimary)
            if isOwnWallet {
                Button {
                    showExpenseSheet = true
                } label: {
                    Text("Registrera köp")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color(red: 0x38/255, green: 0xA1/255, blue: 0x69/255))
                .disabled(balance.balance <= 0)
            } else {
                Button {
                    showGiveMoneySheet = true
                } label: {
                    Label("Ge pengar", systemImage: "plus.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(Color(red: 0x38/255, green: 0xA1/255, blue: 0x69/255))
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(Color.white.opacity(0.75)))
    }

    // MARK: - Savings goals

    private var savingsGoalsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Sparmål")
                    .font(.headline)
                    .foregroundColor(cardTextPrimary)
                Spacer()
                if let b = balance, b.balance > 0,
                   savingsGoals.contains(where: { $0.isActive && !$0.isCompleted }) {
                    Button("Fördela") { showAllocateSheet = true }
                        .font(.subheadline)
                }
                Button("+ Nytt mål") { showCreateGoalSheet = true }
                    .font(.subheadline)
            }

            let activeGoals = savingsGoals.filter { $0.isActive && !$0.isCompleted }
            let doneGoals = savingsGoals.filter { $0.isCompleted || $0.isPurchased }

            if savingsGoals.isEmpty {
                Text("Inga sparmål ännu.")
                    .font(.subheadline)
                    .foregroundColor(cardTextSecondary)
            } else {
                ForEach(activeGoals) { goal in
                    savingsGoalRow(goal: goal, dimmed: false)
                }
                ForEach(doneGoals.prefix(3)) { goal in
                    savingsGoalRow(goal: goal, dimmed: true)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(Color.white.opacity(0.75)))
    }

    private func savingsGoalRow(goal: SavingsGoalResponseDTO, dimmed: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("\(goal.emoji ?? "🎯") \(goal.name)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(cardTextPrimary)
                Spacer()
                if goal.isPurchased {
                    Text("🛒 Köpt").font(.caption).foregroundColor(cardTextSecondary)
                } else if goal.isCompleted {
                    Text("✓ Klar").font(.caption).foregroundColor(.green)
                } else {
                    Text("\(goal.currentAmount) / \(goal.targetAmount) kr")
                        .font(.caption)
                        .foregroundColor(cardTextSecondary)
                }
            }
            if !goal.isCompleted {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 6).fill(Color.gray.opacity(0.2))
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Color(red: 0x48/255, green: 0xBB/255, blue: 0x78/255))
                            .frame(width: geo.size.width * CGFloat(goal.progressPercentage) / 100)
                    }
                }
                .frame(height: 10)
                Text("\(goal.remainingAmount) kr kvar")
                    .font(.caption2)
                    .foregroundColor(cardTextSecondary)
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 10, style: .continuous)
            .fill(Color(red: 0xE0/255, green: 0xF2/255, blue: 0xFE/255).opacity(dimmed ? 0.5 : 1.0)))
    }

    // MARK: - Transactions

    private var transactionsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Senaste transaktioner")
                .font(.headline)
                .foregroundColor(cardTextPrimary)

            if transactions.isEmpty {
                Text("Inga transaktioner ännu.")
                    .font(.subheadline)
                    .foregroundColor(cardTextSecondary)
            } else {
                ForEach(transactions.prefix(20)) { t in
                    transactionRow(t)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(Color.white.opacity(0.75)))
    }

    private func transactionRow(_ t: WalletTransactionResponseDTO) -> some View {
        let isSavings = t.transactionType == "SAVINGS_ALLOCATION"
        let isExpense = t.amount < 0
        let accentColor: Color = isSavings ? .blue : (isExpense ? .red : .green)
        let sign = t.amount >= 0 ? "+" : ""
        let dateText = formatDate(t.createdAt)

        return HStack {
            Rectangle()
                .fill(accentColor)
                .frame(width: 4)
                .cornerRadius(2)
            VStack(alignment: .leading, spacing: 2) {
                Text(t.description ?? localizedType(t.transactionType))
                    .font(.subheadline)
                    .foregroundColor(cardTextPrimary)
                Text(dateText)
                    .font(.caption2)
                    .foregroundColor(cardTextSecondary)
            }
            Spacer()
            Text("\(sign)\(t.amount) kr")
                .font(.subheadline.weight(.semibold))
                .foregroundColor(accentColor)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 10, style: .continuous)
            .fill(accentColor.opacity(0.08)))
    }

    // MARK: - Background gradient (mirrors ChildDashboardView)

    private func backgroundGradient(for petType: String?) -> LinearGradient {
        switch petType?.lowercased() {
        case "dragon":  return gradient(0x4C1D95, 0x1E293B)
        case "cat":     return gradient(0xFDE68A, 0xF97316)
        case "dog":     return gradient(0xBBF7D0, 0x22C55E)
        case "bird":    return gradient(0xBFDBFE, 0x2563EB)
        case "rabbit":  return gradient(0xFCE7F3, 0xEC4899)
        case "bear":    return gradient(0xFEF3C7, 0x92400E)
        case "snake":   return gradient(0xDCFCE7, 0x15803D)
        case "panda":   return gradient(0xE5E7EB, 0x111827)
        case "slot":    return gradient(0xE5E7EB, 0x6B7280)
        case "hydra":   return gradient(0xC4B5FD, 0x4C1D95)
        case "unicorn": return gradient(0xFDE68A, 0xF9A8D4)
        case "kapybara":return gradient(0xDCFCE7, 0x22C55E)
        default:        return gradient(0xE0E7FF, 0xE0F2FF)
        }
    }

    private func gradient(_ top: UInt32, _ bottom: UInt32) -> LinearGradient {
        LinearGradient(
            colors: [Color(hex: top), Color(hex: bottom)],
            startPoint: .top, endPoint: .bottom
        )
    }

    // MARK: - Helpers

    private func formatDate(_ iso: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date else { return iso }
        let out = DateFormatter()
        out.locale = Locale(identifier: "sv_SE")
        out.dateFormat = "d MMM, HH:mm"
        return out.string(from: date)
    }

    private func localizedType(_ type: String) -> String {
        switch type {
        case "ALLOWANCE": return "Fickpengar"
        case "EXPENSE": return "Köp"
        case "SAVINGS_ALLOCATION": return "Sparmål"
        default: return type
        }
    }

    // MARK: - Load

    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            async let balanceResp = ApiClient.shared.send(WalletBalanceResponseDTO.self,
                path: isOwnWallet ? "wallet/balance" : "wallet/members/\(childId)/balance", method: "GET")
            async let txResp = ApiClient.shared.send([WalletTransactionResponseDTO].self,
                path: isOwnWallet ? "wallet/transactions" : "wallet/members/\(childId)/transactions",
                method: "GET", queryItems: [URLQueryItem(name: "limit", value: "20")])
            async let goalsResp: [SavingsGoalResponseDTO] = isOwnWallet
                ? WalletRepository.fetchSavingsGoals()
                : []
            async let catsResp: [ExpenseCategoryResponseDTO] = isOwnWallet
                ? WalletRepository.fetchExpenseCategories()
                : []
            async let petResp: PetResponseDTO? = try? ApiClient.shared
                .send(PetResponseDTO.self, path: "pets/current", method: "GET")
            async let notifResp: [WalletNotificationResponseDTO] = isOwnWallet
                ? (try? WalletRepository.fetchUnshownNotifications()) ?? []
                : []

            let (b, tx, goals, cats, pet, notifs) = try await (balanceResp, txResp, goalsResp, catsResp, petResp, notifResp)

            balance = b
            transactions = tx
            savingsGoals = goals
            categories = cats
            petType = pet?.petType
            pendingNotification = notifs.first
            isLoading = false
        } catch {
            errorMessage = "Kunde inte ladda plånboken."
            isLoading = false
        }
    }
}

// MARK: - Color hex helper

private extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

// MARK: - Record Expense Sheet

private struct RecordExpenseSheet: View {
    let currentBalance: Int
    let categories: [ExpenseCategoryResponseDTO]
    var onDismiss: () -> Void
    var onSuccess: () -> Void

    @State private var amount = ""
    @State private var description = ""
    @State private var selectedCategoryId: String?
    @State private var isSaving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Belopp") {
                    TextField("Belopp (kr)", text: $amount)
                        .keyboardType(.numberPad)
                }
                Section("Beskrivning (valfritt)") {
                    TextField("T.ex. godis", text: $description)
                }
                if !categories.isEmpty {
                    Section("Kategori") {
                        ForEach(categories) { cat in
                            HStack {
                                Text("\(cat.emoji ?? "") \(cat.name)")
                                Spacer()
                                if selectedCategoryId == cat.id {
                                    Image(systemName: "checkmark").foregroundColor(.blue)
                                }
                            }
                            .contentShape(Rectangle())
                            .onTapGesture { selectedCategoryId = cat.id }
                        }
                    }
                }
                if let error {
                    Section { Text(error).foregroundColor(.red) }
                }
            }
            .navigationTitle("Registrera köp")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt", action: onDismiss)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Sparar…" : "Betala") {
                        Task { await submit() }
                    }
                    .disabled(isSaving)
                }
            }
            .onAppear {
                selectedCategoryId = categories.first?.id
            }
        }
    }

    private func submit() async {
        guard let amt = Int(amount), amt > 0 else { error = "Ange ett belopp"; return }
        guard amt <= currentBalance else { error = "Du har bara \(currentBalance) kr"; return }
        isSaving = true
        error = nil
        do {
            try await WalletRepository.recordExpense(
                amount: amt,
                description: description.isEmpty ? nil : description,
                categoryId: selectedCategoryId
            )
            onSuccess()
        } catch {
            self.error = "Kunde inte registrera köpet."
        }
        isSaving = false
    }
}

// MARK: - Create Savings Goal Sheet

private struct CreateSavingsGoalSheet: View {
    var onDismiss: () -> Void
    var onSuccess: () -> Void

    @State private var name = ""
    @State private var targetAmount = ""
    @State private var emoji = ""
    @State private var isSaving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Namn") {
                    TextField("T.ex. Ny cykel", text: $name)
                }
                Section("Målbelopp (kr)") {
                    TextField("500", text: $targetAmount)
                        .keyboardType(.numberPad)
                }
                Section("Emoji (valfritt)") {
                    let emojis = ["🎮","🚲","🎸","⚽","🏊","🎁","🍕","🏆","🚀","🦄",
                                  "🎨","🎬","🎤","🎲","🧸","🎀","🌈","🦋","🐉","🍦",
                                  "🏖️","🛹","🎻","🥁","🎹","🎯","🎠","🎢","🎡","🎪"]
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 8) {
                        ForEach(emojis, id: \.self) { e in
                            Text(e)
                                .font(.title2)
                                .padding(6)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(emoji == e ? Color.blue.opacity(0.2) : Color.clear)
                                )
                                .onTapGesture { emoji = emoji == e ? "" : e }
                        }
                    }
                }
                if let error {
                    Section { Text(error).foregroundColor(.red) }
                }
            }
            .navigationTitle("Skapa sparmål")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt", action: onDismiss)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Skapar…" : "Spara") {
                        Task { await submit() }
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    private func submit() async {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else { error = "Ange ett namn"; return }
        guard let amt = Int(targetAmount), amt > 0 else { error = "Målbeloppet måste vara större än 0"; return }
        isSaving = true
        error = nil
        do {
            _ = try await WalletRepository.createSavingsGoal(
                name: name.trimmingCharacters(in: .whitespaces),
                targetAmount: amt,
                emoji: emoji.isEmpty ? nil : emoji
            )
            onSuccess()
        } catch {
            self.error = "Kunde inte skapa sparmålet."
        }
        isSaving = false
    }
}

// MARK: - Allocate To Goals Sheet

private struct AllocateToGoalsSheet: View {
    let currentBalance: Int
    let activeGoals: [SavingsGoalResponseDTO]
    var onDismiss: () -> Void
    var onSuccess: () -> Void

    @State private var amounts: [String: String] = [:]
    @State private var isSaving = false
    @State private var error: String?

    private var totalAllocated: Int { amounts.values.compactMap { Int($0) }.reduce(0, +) }
    private var remaining: Int { currentBalance - totalAllocated }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Du har \(currentBalance) kr. Fördela till dina sparmål.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Section("Sparmål") {
                    ForEach(activeGoals) { goal in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(goal.emoji ?? "🎯") \(goal.name) (max \(goal.remainingAmount) kr)")
                                .font(.subheadline)
                            TextField("0 kr", text: Binding(
                                get: { amounts[goal.id] ?? "" },
                                set: { amounts[goal.id] = $0.filter { $0.isNumber } }
                            ))
                            .keyboardType(.numberPad)
                        }
                        .padding(.vertical, 4)
                    }
                }
                Section {
                    HStack {
                        Text("Totalt att fördela")
                        Spacer()
                        Text("\(totalAllocated) kr")
                            .foregroundColor(totalAllocated > currentBalance ? .red : .primary)
                    }
                    HStack {
                        Text("Kvar på kontot")
                        Spacer()
                        Text("\(remaining) kr")
                            .foregroundColor(remaining < 0 ? .red : .green)
                    }
                }
                if let error {
                    Section { Text(error).foregroundColor(.red) }
                }
            }
            .navigationTitle("Fördela till mål")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt", action: onDismiss)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Sparar…" : "Fördela") {
                        Task { await submit() }
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    private func submit() async {
        let allocations = activeGoals.compactMap { goal -> (goalId: String, amount: Int)? in
            guard let amt = Int(amounts[goal.id] ?? ""), amt > 0 else { return nil }
            return (goalId: goal.id, amount: amt)
        }
        guard !allocations.isEmpty else { error = "Fördela minst 1 kr till ett mål"; return }
        guard totalAllocated <= currentBalance else { error = "Du kan inte fördela mer än du har"; return }
        isSaving = true
        error = nil
        do {
            try await WalletRepository.allocateToGoals(allocations: allocations)
            onSuccess()
        } catch ApiError.httpError(let status, let data) {
            let body = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
            error = "Fel \(status)\(body.isEmpty ? "" : ": \(body)")"
        } catch {
            self.error = "Kunde inte fördela pengar."
        }
        isSaving = false
    }
}

// MARK: - Give Money Sheet

private struct GiveMoneySheet: View {
    let childName: String
    let childId: String
    var onDismiss: () -> Void
    var onSuccess: () -> Void

    @State private var amount = ""
    @State private var description = ""
    @State private var isSaving = false
    @State private var error: String?

    private let suggestions: [(label: String, amount: Int, description: String)] = [
        ("Månadspeng",  120, "Månadspeng"),
        ("Veckopeng",    30, "Veckopeng"),
        ("Belöning",     50, "Belöning"),
        ("Extra",        20, "Extra"),
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(suggestions, id: \.label) { s in
                                let isSelected = description == s.description && amount == "\(s.amount)"
                                let chipColor = isSelected ? Color.green.opacity(0.2) : Color(.systemGray5)
                                Button {
                                    amount = "\(s.amount)"
                                    description = s.description
                                } label: {
                                    VStack(spacing: 2) {
                                        Text(s.label).font(.subheadline.weight(.medium))
                                        Text("\(s.amount) kr").font(.caption).foregroundColor(.secondary)
                                    }
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(
                                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                                            .fill(chipColor)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                } header: {
                    Text("Snabbval")
                }

                Section("Belopp (kr)") {
                    TextField("120", text: $amount)
                        .keyboardType(.numberPad)
                }

                Section("Förklaring") {
                    TextField("T.ex. Månadspeng oktober", text: $description)
                }

                if let error {
                    Section { Text(error).foregroundColor(.red) }
                }
            }
            .navigationTitle("Ge pengar till \(childName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt", action: onDismiss)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Skickar…" : "Ge pengar") {
                        Task { await submit() }
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    private func submit() async {
        guard let amt = Int(amount), amt > 0 else { error = "Ange ett belopp"; return }
        isSaving = true
        error = nil
        do {
            try await WalletRepository.giveAllowance(
                childMemberId: childId,
                amount: amt,
                description: description.isEmpty ? "Pengar" : description
            )
            onSuccess()
        } catch ApiError.httpError(let status, let data) {
            let body = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
            self.error = "Fel \(status)\(body.isEmpty ? "" : ": \(body)")"
        } catch {
            self.error = error.localizedDescription
        }
        isSaving = false
    }
}

// MARK: - Allowance Notification Overlay

private struct AllowanceNotificationOverlay: View {
    let notification: WalletNotificationResponseDTO
    var onClose: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.5).ignoresSafeArea()
                .onTapGesture(perform: onClose)
            VStack(spacing: 16) {
                Text("🎉").font(.system(size: 64))
                Text("Du fick pengar!")
                    .font(.title2.weight(.bold))
                Text("+\(notification.amount) kr")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(Color(red: 0x48/255, green: 0xBB/255, blue: 0x78/255))
                if let desc = notification.description {
                    Text(desc).font(.body).foregroundColor(.secondary)
                }
                Button("OK", action: onClose)
                    .buttonStyle(.borderedProminent)
                    .tint(Color(red: 0x38/255, green: 0xA1/255, blue: 0x69/255))
                    .controlSize(.large)
            }
            .padding(32)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color.white))
            .padding(24)
        }
    }
}
