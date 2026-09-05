import SwiftUI

/// Whose screen this is, as far as colour is concerned.
///
/// A child's own wallet -- and a parent previewing it through "visa som barn" -- keeps
/// the animal's colours, because that is the child's identity. A parent administering
/// the wallet gets the season, like every other screen a parent opens.
///
/// The distinction is not "who is logged in": it is which way in was taken. The same
/// parent, on the same phone, sees two different palettes depending on whether they
/// came here to look at the child's screen or to change what is in it.
private struct WalletSkin {
    let parentView: Bool
    let palette: SeasonPalette

    /// The near-black and grey that read on a white card floating over an animal
    /// gradient. Not from the palette: this card is not on the season's ground.
    private static let cardInk = Color(hex: 0xFF1C1917)
    private static let cardInkSoft = Color(hex: 0xFF57534E)
    private static let cardInkFaint = Color(hex: 0xFF8A8480)

    var surface: Color { parentView ? palette.surface : Color.white.opacity(0.82) }
    var ink: Color { parentView ? palette.ink : Self.cardInk }
    var inkSoft: Color { parentView ? palette.inkSoft : Self.cardInkSoft }
    var inkFaint: Color { parentView ? palette.inkFaint : Self.cardInkFaint }

    /// White reads on every animal gradient; on a light season ground it does not.
    var onBackground: Color { parentView ? palette.ink : .white }

    /// Money colours mean something, so they stay -- but a dark card needs the lighter
    /// end of each hue or the amount disappears into it.
    private var onDark: Bool { parentView && palette.dark }
    var moneyIn: Color { onDark ? Color(hex: 0xFF6FD38F) : Color(hex: 0xFF22C55E) }
    var moneyOut: Color { onDark ? Color(hex: 0xFFF58A8A) : Color(hex: 0xFFEF4444) }
    var moneySaved: Color { onDark ? Color(hex: 0xFF7FB0F5) : Color(hex: 0xFF2563EB) }
}

/// One child's wallet, from either side of it.
///
/// Three ways in, and they are not the same screen:
///
/// - the child, on their own phone (`isOwnWallet`): animal colours, savings goals,
///   "Registrera köp", and no sign that an automatic allowance exists;
/// - a parent previewing through "visa som barn" (`fromChildView`): what the child
///   sees, so still animal colours, plus the allowance line to read but not to change;
/// - a parent administering, straight from the overview: the season, "Ge pengar", and
///   the allowance line as a way through to the schedule.
struct ChildWalletView: View {
    let childName: String
    let childId: String
    let isOwnWallet: Bool
    var onBack: () -> Void = {}
    /// Opened from inside the child's own view rather than from the family overview.
    /// Declared after `onBack` so the memberwise initialiser keeps the shape the
    /// existing callers already use.
    var fromChildView: Bool = false
    var onOpenRecurringAllowance: () -> Void = {}

    /// Non-nil renders these values instead of calling the network. Only `fixture()`
    /// sets it; it stays a plain stored property rather than living behind `#if DEBUG`
    /// so the memberwise initialiser has the same shape in both configurations.
    var preloaded: Content?

    /// Everything the screen draws, so a fixture can hand it over whole.
    struct Content {
        var balance: Int
        var transactions: [WalletTransactionResponseDTO]
        var savingsGoals: [SavingsGoalResponseDTO]
        var petType: String?
        var recurring: RecurringAllowanceDetailDTO?

        init(
            balance: Int,
            transactions: [WalletTransactionResponseDTO] = [],
            savingsGoals: [SavingsGoalResponseDTO] = [],
            petType: String? = nil,
            recurring: RecurringAllowanceDetailDTO? = nil
        ) {
            self.balance = balance
            self.transactions = transactions
            self.savingsGoals = savingsGoals
            self.petType = petType
            self.recurring = recurring
        }
    }

    @Environment(\.seasonPalette) private var palette

    @State private var balance: WalletBalanceResponseDTO?
    @State private var savingsGoals: [SavingsGoalResponseDTO] = []
    @State private var transactions: [WalletTransactionResponseDTO] = []
    @State private var categories: [ExpenseCategoryResponseDTO] = []
    @State private var recurring: RecurringAllowanceDetailDTO?
    @State private var petType: String?
    @State private var pendingNotification: WalletNotificationResponseDTO?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var showExpenseSheet = false
    @State private var showCreateGoalSheet = false
    @State private var showGiveMoneySheet = false
    @State private var showAllocateSheet = false

    /// A parent who came here to administer, rather than to look at the child's screen.
    /// The one flag the whole screen turns on.
    private var isParentAdmin: Bool { !isOwnWallet && !fromChildView }

    private var skin: WalletSkin {
        WalletSkin(parentView: isParentAdmin, palette: palette)
    }

    var body: some View {
        ZStack {
            background.ignoresSafeArea()

            VStack(spacing: 0) {
                // The season's title bar for a parent administering, because that is
                // what every other screen a parent opens wears. The child's wallet
                // keeps its own header inside the scroll: the bar's gradient is the
                // season, and it would sit on the animal's colours as a foreign band.
                if isParentAdmin {
                    SeasonHeaderBar(title: "\(childName) – Plånbok", onBack: onBack)
                }

                if isLoading {
                    ProgressView()
                        .tint(skin.onBackground)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let errorMessage {
                    errorState(errorMessage)
                } else {
                    scrollingContent
                }
            }
        }
        .task { await load() }
        .sheet(isPresented: $showExpenseSheet) {
            RecordExpenseSheet(
                currentBalance: balance?.balance ?? 0,
                categories: categories,
                // Nil means "my own wallet". A parent recording a purchase has to name
                // the child, or the money leaves the parent's wallet instead.
                memberId: isOwnWallet ? nil : childId,
                childName: isOwnWallet ? nil : childName,
                onDismiss: { showExpenseSheet = false },
                onSuccess: { showExpenseSheet = false; Task { await load(force: true) } }
            )
        }
        .sheet(isPresented: $showCreateGoalSheet) {
            CreateSavingsGoalSheet(
                onDismiss: { showCreateGoalSheet = false },
                onSuccess: { showCreateGoalSheet = false; Task { await load(force: true) } }
            )
        }
        .sheet(isPresented: $showAllocateSheet) {
            AllocateToGoalsSheet(
                currentBalance: balance?.balance ?? 0,
                activeGoals: savingsGoals.filter { $0.isActive && !$0.isCompleted },
                onDismiss: { showAllocateSheet = false },
                onSuccess: { showAllocateSheet = false; Task { await load(force: true) } }
            )
        }
        .sheet(isPresented: $showGiveMoneySheet) {
            GiveMoneySheet(
                childName: childName,
                childId: childId,
                onDismiss: { showGiveMoneySheet = false },
                onSuccess: { showGiveMoneySheet = false; Task { await load(force: true) } }
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

    private var background: some View {
        Group {
            if isParentAdmin {
                palette.pageBg
            } else {
                backgroundGradient(for: petType)
            }
        }
    }

    private var scrollingContent: some View {
        ScrollView {
            VStack(spacing: 16) {
                if !isParentAdmin {
                    header
                }
                if let balance {
                    balanceCard(balance: balance)
                }
                // Never rendered where a child can see it. The server refuses a child
                // on the endpoint too; hiding the row is about not putting the amounts
                // in front of the person they are about.
                if !isOwnWallet {
                    recurringAllowanceRow
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

    private func errorState(_ message: String) -> some View {
        VStack(spacing: 12) {
            Text(message)
                .font(.subheadline)
                .foregroundStyle(skin.onBackground)
                .multilineTextAlignment(.center)
            Button("Försök igen") { Task { await load(force: true) } }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(skin.onBackground)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Header (child's own screen only)

    private var header: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.backward")
                Text("Tillbaka")
            }
            .foregroundStyle(skin.onBackground)
            Spacer()
            Text("\(childName) – Plånbok")
                .font(.title2.weight(.bold))
                .foregroundStyle(skin.onBackground)
        }
        .padding(.top, 16)
    }

    // MARK: - Balance card

    private func balanceCard(balance: WalletBalanceResponseDTO) -> some View {
        WalletCard(skin: skin) {
            Text("Saldo")
                .font(.subheadline)
                .foregroundStyle(skin.inkSoft)
            Text("\(balance.balance) kr")
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(skin.ink)

            Spacer().frame(height: 4)

            // Giving money is a decision a parent makes from their own side of the app,
            // so it appears only when administering. Recording a purchase is something
            // you do standing next to the child, so it belongs on every route in.
            if isParentAdmin {
                walletButton("Ge pengar", fill: giveMoneyGreen) {
                    showGiveMoneySheet = true
                }
                Spacer().frame(height: 8)
            }

            walletButton(
                "Registrera köp",
                fill: isParentAdmin ? recordPurchaseBlue : giveMoneyGreen,
                enabled: balance.balance > 0
            ) {
                showExpenseSheet = true
            }
        }
    }

    /// The two filled buttons on the balance card. Their colours are semantic -- green
    /// is money arriving, blue is the secondary of a pair -- so they do not follow the
    /// season; with one button on the card, that one is green whatever the route in.
    private var giveMoneyGreen: Color { Color(hex: 0xFF38A169) }
    private var recordPurchaseBlue: Color { Color(hex: 0xFF2B6CB0) }

    private func walletButton(
        _ title: String,
        fill: Color,
        enabled: Bool = true,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(
                    RoundedRectangle(cornerRadius: 23, style: .continuous)
                        .fill(fill.opacity(enabled ? 1 : 0.4))
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    // MARK: - Automatic allowance

    /// Where the automatic allowance lives: one line in the wallet, because the wallet
    /// is where a parent already goes to think about money.
    ///
    /// Inside "visa som barn" it is a line to read, not a way through: the arrangement
    /// is worth seeing there, changing it is not what that view is for.
    private var recurringAllowanceRow: some View {
        Group {
            if isParentAdmin {
                Button(action: onOpenRecurringAllowance) {
                    allowanceRowBody(chevron: true)
                }
                .buttonStyle(.plain)
            } else {
                // A plain row rather than a disabled button: a greyed-out control reads
                // as something that is temporarily out of order, and this is simply not
                // a control here.
                allowanceRowBody(chevron: false)
            }
        }
    }

    private func allowanceRowBody(chevron: Bool) -> some View {
        let active = recurring?.active == true
        return HStack(spacing: 12) {
            Image(systemName: "calendar")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(active ? Color(hex: 0xFF38A169) : skin.inkFaint)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text("Automatisk utbetalning")
                    .font(.system(size: 14.5, weight: .semibold))
                    .foregroundStyle(skin.ink)
                // The subtitle carries the date rather than the amount. A parent
                // checking that it is on needs to know when; a parent who wants to
                // change the amount is tapping through anyway.
                Text(AllowanceDates.describe(recurring))
                    .font(.caption)
                    .foregroundStyle(active ? skin.inkSoft : skin.inkFaint)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if chevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(skin.inkFaint)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(skin.surface)
        )
    }

    // MARK: - Savings goals

    private var savingsGoalsSection: some View {
        WalletCard(skin: skin) {
            HStack {
                Text("Sparmål")
                    .font(.headline)
                    .foregroundStyle(skin.ink)
                Spacer()
                if let balance, balance.balance > 0,
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
                    .foregroundStyle(skin.inkSoft)
            } else {
                ForEach(activeGoals) { goal in
                    savingsGoalRow(goal: goal, dimmed: false)
                }
                ForEach(doneGoals.prefix(3)) { goal in
                    savingsGoalRow(goal: goal, dimmed: true)
                }
            }
        }
    }

    private func savingsGoalRow(goal: SavingsGoalResponseDTO, dimmed: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("\(goal.emoji ?? "🎯") \(goal.name)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(skin.ink)
                Spacer()
                if goal.isPurchased {
                    Text("🛒 Köpt").font(.caption).foregroundStyle(skin.inkSoft)
                } else if goal.isCompleted {
                    Text("✓ Klar").font(.caption).foregroundStyle(skin.moneyIn)
                } else {
                    Text("\(goal.currentAmount) / \(goal.targetAmount) kr")
                        .font(.caption)
                        .foregroundStyle(skin.inkSoft)
                }
            }
            if !goal.isCompleted {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.08))
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Color(hex: 0xFF48BB78))
                            .frame(width: geo.size.width * CGFloat(goal.progressPercentage) / 100)
                    }
                }
                .frame(height: 10)
                Text("\(goal.remainingAmount) kr kvar")
                    .font(.caption2)
                    .foregroundStyle(skin.inkSoft)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color(hex: 0xFFE0F2FE).opacity(dimmed ? 0.5 : 1.0))
        )
        .opacity(dimmed ? 0.7 : 1)
    }

    // MARK: - Transactions

    private var transactionsSection: some View {
        WalletCard(skin: skin) {
            Text("Senaste transaktioner")
                .font(.headline)
                .foregroundStyle(skin.ink)

            if transactions.isEmpty {
                Text("Inga transaktioner ännu.")
                    .font(.subheadline)
                    .foregroundStyle(skin.inkSoft)
            } else {
                ForEach(transactions.prefix(20)) { transaction in
                    transactionRow(transaction)
                }
            }
        }
    }

    private func transactionRow(_ transaction: WalletTransactionResponseDTO) -> some View {
        let isSavings = transaction.transactionType == "SAVINGS_ALLOCATION"
        let isExpense = transaction.amount < 0
        let accentColor: Color = isSavings
            ? skin.moneySaved
            : (isExpense ? skin.moneyOut : skin.moneyIn)
        let sign = transaction.amount >= 0 ? "+" : ""

        return HStack(spacing: 0) {
            Rectangle()
                .fill(accentColor)
                .frame(width: 4)
            VStack(alignment: .leading, spacing: 2) {
                Text(transaction.description ?? localizedType(transaction.transactionType))
                    .font(.subheadline)
                    .foregroundStyle(skin.ink)
                Text(formatDate(transaction.createdAt))
                    .font(.caption2)
                    .foregroundStyle(skin.inkSoft)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            Spacer()
            Text("\(sign)\(transaction.amount) kr")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(accentColor)
                .padding(.trailing, 12)
        }
        .background(accentColor.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
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
        // The species table above is written as six-digit RGB, while the shared
        // initialiser in SeasonTheme reads ARGB so it can carry the palette's one
        // translucent value. Passing these unqualified would give every animal an
        // alpha of zero -- compiles, renders nothing.
        LinearGradient(
            colors: [Color(hex: opaque(top)), Color(hex: opaque(bottom))],
            startPoint: .top, endPoint: .bottom
        )
    }

    private func opaque(_ rgb: UInt32) -> UInt32 { 0xFF00_0000 | rgb }

    // MARK: - Helpers

    private func formatDate(_ iso: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = formatter.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
        guard let date else { return iso }
        let out = DateFormatter()
        out.locale = AllowanceDates.swedish
        out.dateFormat = "d MMM, HH:mm"
        return out.string(from: date)
    }

    private func localizedType(_ type: String) -> String {
        switch type {
        case "ALLOWANCE": return "Fickpengar"
        case "EXPENSE": return "Köp"
        case "SAVINGS_ALLOCATION": return "Sparmål"
        // TransactionType har fem värden, inte tre. De två sista syntes aldrig här, så
        // MANUAL_ADJUSTMENT stod med versaler och understreck i barnets egen plånbok.
        case "MANUAL_ADJUSTMENT": return "Justering"
        case "DELETION": return "Borttagen"
        // Aldrig råvärdet. En okänd typ ska se tråkig ut, inte teknisk.
        default: return "Övrigt"
        }
    }

    // MARK: - Load

    private func load(force: Bool = false) async {
        if let preloaded, !force {
            balance = WalletBalanceResponseDTO(id: nil, memberId: childId, balance: preloaded.balance)
            transactions = preloaded.transactions
            savingsGoals = preloaded.savingsGoals
            petType = preloaded.petType
            recurring = preloaded.recurring
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil
        do {
            // Started together, read one at a time. The three that matter are allowed
            // to fail the whole screen; the four that only decorate it answer with
            // nothing rather than turning a readable balance into an error page.
            async let balanceResp = fetchBalance()
            async let txResp = fetchTransactions()
            async let goalsResp = fetchSavingsGoals()
            async let catsResp = fetchCategories()
            async let petResp = fetchPetType()
            async let recurringResp = fetchRecurring()
            async let notifResp = fetchNotifications()

            let loadedBalance = try await balanceResp
            let loadedTransactions = try await txResp
            let loadedGoals = try await goalsResp

            balance = loadedBalance
            transactions = loadedTransactions
            savingsGoals = loadedGoals
            categories = await catsResp
            petType = await petResp
            recurring = await recurringResp
            pendingNotification = await notifResp.first
            isLoading = false
        } catch {
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte ladda plånboken.")
            isLoading = false
        }
    }

    private func fetchBalance() async throws -> WalletBalanceResponseDTO {
        if isOwnWallet {
            return try await ApiClient.shared.send(
                WalletBalanceResponseDTO.self,
                path: "wallet/balance",
                method: "GET"
            )
        }
        return try await ParentWalletRepository.fetchBalance(memberId: childId)
    }

    private func fetchTransactions() async throws -> [WalletTransactionResponseDTO] {
        if isOwnWallet {
            return try await ApiClient.shared.send(
                [WalletTransactionResponseDTO].self,
                path: "wallet/transactions",
                method: "GET",
                queryItems: [URLQueryItem(name: "limit", value: "20")]
            )
        }
        return try await ParentWalletRepository.fetchTransactions(memberId: childId)
    }

    /// Savings goals are the child's own, and there is no parent-facing list of them.
    private func fetchSavingsGoals() async throws -> [SavingsGoalResponseDTO] {
        guard isOwnWallet else { return [] }
        return try await WalletRepository.fetchSavingsGoals()
    }

    /// Needed on every route in now: "Registrera köp" is on the card whoever opened it,
    /// and its sheet offers the family's categories.
    private func fetchCategories() async -> [ExpenseCategoryResponseDTO] {
        (try? await WalletRepository.fetchExpenseCategories()) ?? []
    }

    /// Only drives the gradient, so a parent administering does not need it at all --
    /// their screen wears the season.
    private func fetchPetType() async -> String? {
        guard !isParentAdmin else { return nil }
        if isOwnWallet {
            let pet = try? await ApiClient.shared.send(
                PetResponseDTO.self,
                path: "pets/current",
                method: "GET"
            )
            return pet?.petType
        }
        let pet = try? await PetRepository.fetchPetForMember(memberId: childId)
        return pet?.petType
    }

    /// Parent view only. The server refuses a child on this endpoint, so asking for it
    /// in the child's own wallet would fail every time.
    private func fetchRecurring() async -> RecurringAllowanceDetailDTO? {
        guard !isOwnWallet else { return nil }
        return (try? await RecurringAllowanceRepository.fetch(memberId: childId)) ?? nil
    }

    private func fetchNotifications() async -> [WalletNotificationResponseDTO] {
        guard isOwnWallet else { return [] }
        return (try? await WalletRepository.fetchUnshownNotifications()) ?? []
    }
}

// MARK: - Reusable card

private struct WalletCard<Content: View>: View {
    let skin: WalletSkin
    let content: Content

    init(skin: WalletSkin, @ViewBuilder content: () -> Content) {
        self.skin = skin
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(skin.surface)
        )
    }
}

// MARK: - Record Expense Sheet

private struct RecordExpenseSheet: View {
    let currentBalance: Int
    let categories: [ExpenseCategoryResponseDTO]
    /// Nil when the buyer is the person holding the phone. Non-nil names the child the
    /// purchase is for: "wallet/expense" always charges whoever the device token
    /// belongs to, so a parent recording a purchase without this empties their own
    /// wallet and leaves the child's balance untouched.
    let memberId: String?
    let childName: String?
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
                if let childName {
                    Section {
                        Text("Dras från \(childName)s saldo: \(currentBalance) kr.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
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
        guard amt <= currentBalance else {
            error = childName.map { "\($0) har bara \(currentBalance) kr" } ?? "Du har bara \(currentBalance) kr"
            return
        }
        isSaving = true
        error = nil
        do {
            if let memberId {
                try await ParentWalletRepository.recordExpense(
                    memberId: memberId,
                    amount: amt,
                    description: description.isEmpty ? nil : description,
                    categoryId: selectedCategoryId
                )
            } else {
                try await WalletRepository.recordExpense(
                    amount: amt,
                    description: description.isEmpty ? nil : description,
                    categoryId: selectedCategoryId
                )
            }
            onSuccess()
        } catch {
            self.error = ApiErrors.message(error, fallback: "Kunde inte registrera köpet.")
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

// MARK: - Fixture

#if DEBUG
extension ChildWalletView {

    /// Which way into the wallet is being photographed. The screen differs by more than
    /// a colour between them, so each needs its own entry.
    enum FixtureViewer {
        /// A parent, straight from the overview: season, "Ge pengar", allowance row.
        case parentAdmin
        /// The child, on their own phone: animal colours, savings goals, no allowance row.
        case child
        /// A parent inside "visa som barn": the child's screen, allowance row unarmed.
        case childPreview
    }

    /// The screen with sample data and no session, so it can be photographed.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen behind
    /// a login cannot be reached to be looked at at all. This is the way in -- see
    /// ScreenHarness in KidQuestApp.swift.
    ///
    /// The transactions are deliberately mixed: an allowance, a purchase, a gift and a
    /// savings allocation, so all four accent colours appear rather than a column of
    /// identical green rows.
    static func fixture(viewer: FixtureViewer = .parentAdmin) -> ChildWalletView {
        let transactions: [WalletTransactionResponseDTO] = [
            fixtureTransaction(id: "t1", amount: 120, type: "ALLOWANCE",
                               text: "Månadspeng AUG", at: "2026-08-29T15:36:00Z"),
            fixtureTransaction(id: "t2", amount: -21, type: "EXPENSE",
                               text: "godis", at: "2026-08-29T15:28:00Z"),
            fixtureTransaction(id: "t3", amount: -200, type: "SAVINGS_ALLOCATION",
                               text: "Till Ny cykel", at: "2026-08-28T18:02:00Z"),
            fixtureTransaction(id: "t4", amount: 120, type: "ALLOWANCE",
                               text: "Juli", at: "2026-08-28T06:42:00Z"),
            fixtureTransaction(id: "t5", amount: 10, type: "ALLOWANCE",
                               text: "Tandfen", at: "2026-07-30T07:16:00Z"),
            fixtureTransaction(id: "t6", amount: 250, type: "ALLOWANCE",
                               text: "Gammelmormor", at: "2026-07-19T13:26:00Z"),
        ]

        let goals: [SavingsGoalResponseDTO] = [
            fixtureGoal(id: "g1", name: "Ny cykel", emoji: "🚲", target: 2500, current: 900),
            fixtureGoal(id: "g2", name: "Nintendo-spel", emoji: "🎮", target: 600, current: 600),
        ]

        // The level kind, because it is the one whose summary line has to say something
        // other than a plain weekday -- "Efter nivå · nästa 1 september".
        let schedule = RecurringAllowanceDetailDTO(
            memberId: "child-1",
            kind: "LEVEL",
            amount: nil,
            weekday: nil,
            dayOfMonth: 1,
            level1: 40, level2: 60, level3: 90, level4: 120, level5: 160,
            active: true,
            nextDueOn: "2026-09-01"
        )

        let ownWallet = viewer == .child
        return ChildWalletView(
            childName: "Signe",
            childId: "child-1",
            isOwnWallet: ownWallet,
            fromChildView: viewer == .childPreview,
            preloaded: Content(
                balance: 2311,
                transactions: transactions,
                savingsGoals: ownWallet ? goals : [],
                petType: "dragon",
                recurring: ownWallet ? nil : schedule
            )
        )
    }

    private static func fixtureTransaction(
        id: String,
        amount: Int,
        type: String,
        text: String,
        at created: String
    ) -> WalletTransactionResponseDTO {
        WalletTransactionResponseDTO(
            id: id,
            walletId: "wallet-1",
            amount: amount,
            transactionType: type,
            description: text,
            categoryId: nil,
            createdByMemberId: nil,
            isDeleted: false,
            deletedAt: nil,
            deletedByMemberId: nil,
            createdAt: created
        )
    }

    private static func fixtureGoal(
        id: String,
        name: String,
        emoji: String,
        target: Int,
        current: Int
    ) -> SavingsGoalResponseDTO {
        SavingsGoalResponseDTO(
            id: id,
            memberId: "child-1",
            name: name,
            targetAmount: target,
            currentAmount: current,
            emoji: emoji,
            isActive: true,
            isCompleted: current >= target,
            isPurchased: false,
            progressPercentage: target > 0 ? min(100, current * 100 / target) : 0,
            remainingAmount: max(0, target - current),
            createdAt: "2026-06-01T10:00:00Z",
            updatedAt: "2026-08-28T18:02:00Z"
        )
    }
}
#endif
