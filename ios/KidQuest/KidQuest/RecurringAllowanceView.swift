import SwiftUI

/// Automatic weekly or monthly allowance for one child.
///
/// Reached only from a parent's view of a child's wallet. The server refuses a child on
/// both read and write, so keeping it out of the child's wallet is about not putting the
/// amounts in front of the person they are about -- the actual lock is on the other side.
///
/// The three options are one list rather than three screens: a parent choosing between
/// them wants to compare, so only the chosen one unfolds its fields.
struct RecurringAllowanceView: View {
    let childName: String
    let childId: String
    var onBack: () -> Void = {}
    /// Called after a successful save or switch-off, so the wallet behind can reload
    /// its summary line rather than showing the arrangement that was just replaced.
    var onSaved: () -> Void = {}

    /// Non-nil renders the form from these values instead of calling the network. Only
    /// `fixture()` sets it; it stays a plain stored property rather than living behind
    /// `#if DEBUG` so the memberwise initialiser has the same shape in both configurations.
    var preloaded: Loaded?

    /// What one load of this screen produces: the saved arrangement, if any, and the
    /// level the child is standing on right now.
    struct Loaded {
        var schedule: RecurringAllowanceDetailDTO?
        var currentLevel: Int?

        init(schedule: RecurringAllowanceDetailDTO? = nil, currentLevel: Int? = nil) {
            self.schedule = schedule
            self.currentLevel = currentLevel
        }
    }

    enum Kind: String, CaseIterable {
        case weekly = "WEEKLY"
        case monthly = "MONTHLY"
        case level = "LEVEL"
    }

    @Environment(\.seasonPalette) private var palette

    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var error: String?
    @State private var saved: RecurringAllowanceDetailDTO?
    @State private var currentLevel: Int?
    @State private var confirmDisable = false

    // Form state. Amounts stay strings so a half-typed field is not silently a zero --
    // and they start EMPTY. A pre-filled sum nobody looks at becomes a payment nobody
    // decided; the figures in the mock-up were illustration, not a default.
    @State private var kind: Kind = .weekly
    @State private var amount = ""
    @State private var weekday = 5
    @State private var dayOfMonth = 1
    @State private var levels = Array(repeating: "", count: 5)

    private static let weekdayNames = ["Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön"]

    /// Stays semantic: an amount is green because it is money, not because it is spring.
    private var money: Color {
        palette.dark ? Color(hex: 0xFF6FD38F) : Color(hex: 0xFF38A169)
    }

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(title: "Utbetalningar till \(childName)", onBack: onBack)

            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let loadError {
                errorState(loadError)
            } else {
                form
            }
        }
        .background(palette.pageBg.ignoresSafeArea())
        .task { await load() }
        .alert("Stäng av?", isPresented: $confirmDisable) {
            Button("Avbryt", role: .cancel) {}
            Button("Stäng av", role: .destructive) { Task { await disable() } }
        } message: {
            Text(
                "Inga fler automatiska utbetalningar till \(childName). Pengar som redan "
                + "betalats ut ligger kvar i plånboken, och du kan slå på det igen när du vill."
            )
        }
    }

    // MARK: - The form

    private var form: some View {
        ScrollView {
            VStack(spacing: 10) {
                weeklyCard
                monthlyCard
                levelCard

                if let error {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(palette.danger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 2)
                }

                saveButton
                    .padding(.top, 2)

                if saved?.active == true {
                    Button("Stäng av automatisk utbetalning") { confirmDisable = true }
                        .font(.footnote)
                        .foregroundStyle(palette.accent)
                        .disabled(isSaving)
                        .padding(.vertical, 6)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 28)
        }
    }

    private var weeklyCard: some View {
        OptionCard(
            selected: kind == .weekly,
            icon: "calendar",
            title: "Veckopeng",
            subtitle: "Samma belopp varje vecka",
            onSelect: { select(.weekly) }
        ) {
            FieldLabel("Belopp")
            AmountField(label: "Varje vecka", text: amountBinding, money: money)

            Spacer().frame(height: 14)
            FieldLabel("Vilken dag?")
            HStack(spacing: 6) {
                ForEach(Array(Self.weekdayNames.enumerated()), id: \.offset) { index, label in
                    let day = index + 1
                    DayChip(label: label, selected: weekday == day) {
                        weekday = day
                        error = nil
                    }
                }
            }

            Spacer().frame(height: 12)
            nextPaymentNote
        }
    }

    private var monthlyCard: some View {
        OptionCard(
            selected: kind == .monthly,
            icon: "calendar.badge.clock",
            title: "Månadspeng",
            subtitle: "Samma belopp varje månad",
            onSelect: { select(.monthly) }
        ) {
            FieldLabel("Belopp")
            AmountField(label: "Varje månad", text: amountBinding, money: money)

            Spacer().frame(height: 14)
            FieldLabel("Vilken dag i månaden?")
            DayOfMonthField(day: dayOfMonth) { picked in
                dayOfMonth = picked
                error = nil
            }

            Spacer().frame(height: 12)
            nextPaymentNote
            Text("Går att välja 1–\(AllowanceDates.maxDayOfMonth), så dagen finns varje månad.")
                .font(.caption)
                .foregroundStyle(palette.inkFaint)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var levelCard: some View {
        OptionCard(
            selected: kind == .level,
            icon: "chart.bar.fill",
            title: "Månadspeng utifrån avklarade uppgifter",
            subtitle: "Beloppet beror på vilken nivå \(childName) når",
            onSelect: { select(.level) }
        ) {
            FieldLabel("Belopp per nivå")
            VStack(spacing: 8) {
                ForEach(0..<5, id: \.self) { index in
                    let level = index + 1
                    let here = currentLevel == level
                    AmountField(
                        label: here ? "Nivå \(level) · här nu" : "Nivå \(level)",
                        text: levelBinding(index),
                        money: money,
                        highlighted: here
                    )
                }
            }

            Spacer().frame(height: 12)
            // No day picker here, and none is missing: the level kind always pays on
            // the 1st, because that is the day the level for the month just ended is
            // final and the day it resets.
            Text(
                "\(childName) får beloppet för den nivå hen nått den 1:a. "
                + "Nivån nollställs varje månad, precis som djuret."
            )
            .font(.caption)
            .foregroundStyle(palette.inkFaint)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// "Varje vecka" is a claim; a date is a promise. This turns the choice above into
    /// the day it will actually happen.
    private var nextPaymentNote: some View {
        Text(nextPaymentText)
            .font(.caption)
            .foregroundStyle(palette.inkFaint)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var nextPaymentText: String {
        let today = Date()
        // A saved schedule whose due date has already arrived pays out on the next
        // sweep, so saying "next Friday" there would be a week late.
        if let saved, saved.active, saved.kind == kind.rawValue,
           let due = AllowanceDates.parseIsoDate(saved.nextDueOn),
           due <= Calendar.current.startOfDay(for: today) {
            return "Nästa utbetalning: idag."
        }
        switch kind {
        case .weekly:
            let date = AllowanceDates.nextWeekday(weekday, after: today)
            return "Nästa utbetalning: \(AllowanceDates.format(date, "EEEE d MMMM"))."
        case .monthly, .level:
            let day = kind == .level ? 1 : dayOfMonth
            let date = AllowanceDates.nextDayOfMonth(day, after: today)
            return "Nästa utbetalning: \(AllowanceDates.format(date, "d MMMM"))."
        }
    }

    private var saveButton: some View {
        Button {
            Task { await save() }
        } label: {
            Text(isSaving ? "Sparar…" : "Spara")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(palette.onAccent)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(palette.accent.opacity(isSaving ? 0.6 : 1))
                )
        }
        .buttonStyle(.plain)
        .disabled(isSaving)
    }

    private func errorState(_ message: String) -> some View {
        VStack(spacing: 12) {
            Text(message)
                .font(.subheadline)
                .foregroundStyle(palette.ink)
                .multilineTextAlignment(.center)
            Button("Försök igen") { Task { await load(force: true) } }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.accent)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Bindings

    /// Digits only, and capped: the field is a number of kronor, and a text field that
    /// accepts "12,-" hands the parser something it will read as 12.
    private var amountBinding: Binding<String> {
        Binding(
            get: { amount },
            set: { typed in
                amount = String(typed.filter(\.isNumber).prefix(6))
                error = nil
            }
        )
    }

    private func levelBinding(_ index: Int) -> Binding<String> {
        Binding(
            get: { levels.indices.contains(index) ? levels[index] : "" },
            set: { typed in
                guard levels.indices.contains(index) else { return }
                levels[index] = String(typed.filter(\.isNumber).prefix(6))
                error = nil
            }
        )
    }

    private func select(_ picked: Kind) {
        guard kind != picked else { return }
        kind = picked
        error = nil
    }

    // MARK: - Load

    private func load(force: Bool = false) async {
        if let preloaded {
            apply(preloaded)
            isLoading = false
            return
        }
        guard force || isLoading else { return }
        isLoading = true
        loadError = nil
        do {
            let schedule = try await RecurringAllowanceRepository.fetch(memberId: childId)
            apply(Loaded(schedule: schedule, currentLevel: nil))
            isLoading = false
            // Asked for after the form is on screen: it only marks a row, and waiting
            // for it would hold up everything the parent came here to change.
            currentLevel = await RecurringAllowanceRepository.currentLevel(memberId: childId)
        } catch {
            loadError = ApiErrors.message(error, fallback: "Kunde inte hämta inställningen")
            isLoading = false
        }
    }

    private func apply(_ loaded: Loaded) {
        saved = loaded.schedule
        currentLevel = loaded.currentLevel
        guard let existing = loaded.schedule else { return }
        kind = Kind(rawValue: existing.kind) ?? .weekly
        amount = existing.amount.map(String.init) ?? ""
        weekday = existing.weekday ?? 5
        dayOfMonth = existing.dayOfMonth ?? 1
        levels = [existing.level1, existing.level2, existing.level3, existing.level4, existing.level5]
            .map { $0.map(String.init) ?? "" }
    }

    // MARK: - Save and disable

    private func save() async {
        guard let request = buildRequest() else {
            error = missingFieldMessage()
            return
        }
        isSaving = true
        error = nil
        do {
            saved = try await RecurringAllowanceRepository.save(memberId: childId, request: request)
            isSaving = false
            onSaved()
            onBack()
        } catch {
            self.error = ApiErrors.message(error, fallback: "Kunde inte spara")
            isSaving = false
        }
    }

    private func disable() async {
        isSaving = true
        error = nil
        do {
            try await RecurringAllowanceRepository.disable(memberId: childId)
            isSaving = false
            onSaved()
            onBack()
        } catch {
            self.error = ApiErrors.message(error, fallback: "Kunde inte stänga av")
            isSaving = false
        }
    }

    /// Nil when something required is still blank; the caller turns that into a message.
    private func buildRequest() -> SaveRecurringAllowanceRequestDTO? {
        switch kind {
        case .weekly:
            guard let value = Int(amount), value > 0 else { return nil }
            return SaveRecurringAllowanceRequestDTO(
                kind: kind.rawValue, amount: value, weekday: weekday
            )
        case .monthly:
            guard let value = Int(amount), value > 0 else { return nil }
            return SaveRecurringAllowanceRequestDTO(
                kind: kind.rawValue, amount: value, dayOfMonth: dayOfMonth
            )
        case .level:
            let parsed = levels.map { Int($0) }
            guard parsed.count == 5, !parsed.contains(where: { $0 == nil }) else { return nil }
            return SaveRecurringAllowanceRequestDTO(
                kind: kind.rawValue,
                // The level kind always pays on the 1st: that is the day the level for
                // the month just ended is final, and the day it resets.
                dayOfMonth: 1,
                level1: parsed[0],
                level2: parsed[1],
                level3: parsed[2],
                level4: parsed[3],
                level5: parsed[4]
            )
        }
    }

    /// Says which field is missing rather than "något saknas". The same wording the
    /// server would answer with, without the round trip.
    private func missingFieldMessage() -> String {
        switch kind {
        case .level:
            let blank = levels.firstIndex { Int($0) == nil } ?? 0
            return "Fyll i ett belopp för nivå \(blank + 1)"
        case .weekly, .monthly:
            return amount.isEmpty ? "Fyll i ett belopp" : "Beloppet måste vara större än 0"
        }
    }
}

// MARK: - Option card

/// One of the three kinds. Unselected it is a heading and a radio; selected it unfolds
/// the fields that belong to it, so the list stays a comparison rather than a wall of
/// three half-filled forms.
private struct OptionCard<Content: View>: View {
    @Environment(\.seasonPalette) private var palette

    let selected: Bool
    /// An SF Symbol name.
    let icon: String
    let title: String
    let subtitle: String
    let onSelect: () -> Void
    let content: Content

    init(
        selected: Bool,
        icon: String,
        title: String,
        subtitle: String,
        onSelect: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) {
        self.selected = selected
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.onSelect = onSelect
        self.content = content()
    }

    var body: some View {
        VStack(spacing: 0) {
            if selected {
                LinearGradient(
                    colors: [palette.accent, palette.headerTop],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(height: 3)
            }

            VStack(alignment: .leading, spacing: 0) {
                header
                if selected {
                    Spacer().frame(height: 16)
                    VStack(alignment: .leading, spacing: 0) { content }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, selected ? 16 : 14)
        }
        .frame(maxWidth: .infinity)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(selected ? palette.accent : .clear, lineWidth: 1.5)
        )
        .opacity(selected ? 1 : 0.72)
        // The whole card is the target, not just the radio: it is a choice between
        // three, and a 20pt circle is not what a thumb aims at.
        .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .onTapGesture { if !selected { onSelect() } }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(selected ? palette.accent : palette.inkFaint)
                .frame(width: 22)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(palette.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Radio(selected: selected)
        }
    }
}

private struct Radio: View {
    @Environment(\.seasonPalette) private var palette
    let selected: Bool

    var body: some View {
        Circle()
            .fill(selected ? palette.surface : .clear)
            .frame(width: 20, height: 20)
            .overlay(
                Circle().strokeBorder(
                    selected ? palette.accent : palette.inkFaint,
                    lineWidth: selected ? 6 : 2
                )
            )
    }
}

// MARK: - Fields

private struct FieldLabel: View {
    @Environment(\.seasonPalette) private var palette
    let text: String

    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(palette.inkFaint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.bottom, 6)
    }
}

/// An amount that reads as a line of the schedule rather than as a form field: the
/// label on the left, the money on the right, editable in place.
private struct AmountField: View {
    @Environment(\.seasonPalette) private var palette

    let label: String
    @Binding var text: String
    let money: Color
    var highlighted = false

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.system(size: 13.5, weight: highlighted ? .semibold : .regular))
                .foregroundStyle(highlighted ? palette.accent : palette.inkSoft)
                .lineLimit(1)

            TextField("", text: $text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(money)
                .tint(money)
                .frame(maxWidth: .infinity)
                .overlay(alignment: .trailing) {
                    // A ghosted zero rather than a placeholder string: the field starts
                    // empty on purpose, and "0" as real text would be an amount the
                    // parent never typed.
                    if text.isEmpty {
                        Text("0")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(money.opacity(0.35))
                            .allowsHitTesting(false)
                    }
                }

            Text("kr")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(money)
        }
        .padding(.horizontal, 14)
        .frame(height: 46)
        .background(
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                // The page's own colour, not the card's: a field sits inside a card and
                // has to separate from it.
                .fill(highlighted ? palette.calBg : palette.pageBg)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                .strokeBorder(
                    highlighted ? palette.accent : palette.outlineEdge,
                    lineWidth: highlighted ? 1.5 : 1
                )
        )
        .accessibilityLabel(label)
    }
}

private struct DayChip: View {
    @Environment(\.seasonPalette) private var palette

    let label: String
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Text(label)
                .font(.system(size: 13, weight: selected ? .bold : .medium))
                .foregroundStyle(selected ? palette.accent : palette.inkSoft)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .fill(selected ? palette.calBg : palette.pageBg)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .strokeBorder(
                            selected ? palette.accent : palette.outlineEdge,
                            lineWidth: selected ? 1.5 : 1
                        )
                )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

private struct DayOfMonthField: View {
    @Environment(\.seasonPalette) private var palette

    let day: Int
    let onPick: (Int) -> Void

    var body: some View {
        // A Menu rather than Android's dropdown: 28 choices is what iOS shows in a
        // menu, and a wheel picker for one number would own half the card.
        Menu {
            ForEach(1...AllowanceDates.maxDayOfMonth, id: \.self) { candidate in
                Button("Den \(AllowanceDates.ordinal(candidate))") { onPick(candidate) }
            }
        } label: {
            HStack {
                Text("Den \(AllowanceDates.ordinal(day))")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(palette.ink)
                Spacer()
                Image(systemName: "chevron.down")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.inkFaint)
            }
            .padding(.horizontal, 14)
            .frame(height: 46)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(palette.pageBg)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .strokeBorder(palette.outlineEdge, lineWidth: 1)
            )
        }
        .accessibilityLabel("Vilken dag i månaden")
    }
}

// MARK: - Fixture

#if DEBUG
extension RecurringAllowanceView {

    /// The screen with sample data and no session, so it can be photographed.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen behind
    /// a login cannot be reached to be looked at at all. This is the way in -- see
    /// ScreenHarness in KidQuestApp.swift.
    ///
    /// One entry per kind, because the other two cards are folded shut and no tap can
    /// open them here: without three fixtures, two thirds of this screen is invisible.
    static func fixture(kind: Kind = .weekly) -> RecurringAllowanceView {
        let schedule = RecurringAllowanceDetailDTO(
            memberId: "child-1",
            kind: kind.rawValue,
            amount: kind == .level ? nil : (kind == .weekly ? 50 : 120),
            weekday: kind == .weekly ? 5 : nil,
            dayOfMonth: kind == .weekly ? nil : 1,
            level1: kind == .level ? 40 : nil,
            level2: kind == .level ? 60 : nil,
            level3: kind == .level ? 90 : nil,
            level4: kind == .level ? 120 : nil,
            level5: kind == .level ? 160 : nil,
            active: true,
            // Left out on purpose: with no due date the note falls back to computing
            // the next occurrence from the pickers, which is the line worth looking at.
            nextDueOn: nil
        )
        return RecurringAllowanceView(
            childName: "Signe",
            childId: "child-1",
            // Level 3 of 5: the highlighted row lands in the middle of the table, where
            // it is visibly a marker rather than the first or last row's own styling.
            preloaded: Loaded(schedule: schedule, currentLevel: 3)
        )
    }
}
#endif
