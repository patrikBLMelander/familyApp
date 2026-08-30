import SwiftUI

/// One child's chores: today's list, and the week they recur over.
///
/// A port of the Android ChildTasksScreen. Two things it does are deliberately not
/// carried over, and both are noted where they happen: emoji in the tab labels, and a
/// header row built by hand instead of the shared bar.
///
/// The same screen serves a parent opening a child's list from the dashboard and a
/// child opening their own from theirs, exactly as on Android. Ticking is open to both
/// — a parent marking a chore off from their own phone is how Android and the web both
/// behave. Creating and deleting are not: see `viewerIsAdult`.
struct ChildTasksView: View {
    let childName: String
    let childId: String
    var onBack: () -> Void = {}

    /// Non-nil renders these rows instead of calling the network. Only `fixture()` sets
    /// it; it stays a plain stored property rather than living behind `#if DEBUG` so the
    /// memberwise initialiser has the same shape in both configurations.
    var preloaded: DailyChoreRepositoryIOS.Chores?

    /// Which tab the screen opens on. Only the fixture sets it: the simulator takes no
    /// touch input, so a tab that cannot be tapped cannot otherwise be photographed.
    var initialTab: Tab?

    @Environment(\.seasonPalette) private var palette

    @State private var chores: DailyChoreRepositoryIOS.Chores?
    @State private var isLoading = true
    @State private var errorMessage: String?
    /// A failed tick or delete, said out loud above the list. Not the same thing as
    /// `errorMessage`: the list is still good, one action on it was not.
    @State private var notice: String?
    @State private var chosenTab: Tab?
    @State private var pendingDelete: ChoreRef?
    @State private var showAddSheet = false

    /// The family's list shows the same two tabs, so the type is shared with it.
    typealias Tab = ChoreTab

    private var tab: Tab { chosenTab ?? initialTab ?? .today }

    /// Ticking a chore off is open to whoever holds the phone. Creating and deleting
    /// are not: ContentView routes children away from the parent's view precisely so
    /// they cannot add and remove their own chores, and this is that same screen seen
    /// from the child's side. Android leaves both open to everyone, which is the hole
    /// that routing rule was written to close.
    private var viewerIsAdult: Bool {
        TokenStoreIOS.shared.getSession()?.isChild != true
    }

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(
                title: "\(childName) – Sysslor",
                subtitle: todayLabel,
                onBack: onBack
            ) {
                if viewerIsAdult {
                    addButton
                }
            }

            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let errorMessage {
                errorState(errorMessage)
            } else if let chores {
                tabs

                if let notice {
                    noticeBanner(notice)
                }

                switch tab {
                case .today:
                    todayList(chores)
                case .week:
                    weekList(chores)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.pageBg.ignoresSafeArea())
        // The bar draws its own back control, so the navigation bar would only be an
        // empty strip of a second colour above it. A no-op outside a NavigationStack,
        // which is how the debug harness renders this screen.
        .toolbar(.hidden, for: .navigationBar)
        .task {
            await loadIfNeeded()
        }
        .sheet(isPresented: $showAddSheet) {
            AddChoreSheet(
                childName: childName,
                childId: childId,
                onCreated: { Task { await load() } }
            )
        }
        .confirmationDialog(
            "Ta bort sysslan?",
            isPresented: deleteDialogIsPresented,
            presenting: pendingDelete
        ) { target in
            Button("Ta bort", role: .destructive) {
                pendingDelete = nil
                Task { await delete(target) }
            }
            Button("Avbryt", role: .cancel) {
                pendingDelete = nil
            }
        } message: { target in
            Text("\"\(target.title)\" tas bort för \(childName), tillsammans med historiken över när den blivit gjord. Det går inte att ångra.")
        }
    }

    // MARK: - Header

    private var addButton: some View {
        Button {
            showAddSheet = true
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Ny syssla")
    }

    /// "söndag 30/8" — the same line Android puts under the title.
    private var todayLabel: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "sv_SE")
        formatter.dateFormat = "EEEE d/M"
        return formatter.string(from: Date())
    }

    // MARK: - Tabs

    private var tabs: some View {
        // The pair itself lives in ChoreTabPicker: the family's list shows the same two
        // tabs, and a control copied into both screens is how the two would drift apart.
        ChoreTabPicker(selected: tab) { chosenTab = $0 }
    }

    // MARK: - Today

    private func todayList(_ chores: DailyChoreRepositoryIOS.Chores) -> some View {
        List {
            if chores.today.isEmpty {
                emptyCard("Inga sysslor idag.")
                    .plainChoreRow()
            } else {
                ForEach(chores.today, id: \.chore.id) { item in
                    ChoreRow(item: item) {
                        Task { await toggle(item) }
                    }
                    .plainChoreRow()
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        if viewerIsAdult {
                            // Swipe rather than Android's permanent trash icon on every
                            // row: a bin beside seven chores is seven invitations to
                            // delete a chore while reaching to tick one off. Full swipe
                            // is off because the confirmation is the point.
                            Button(role: .destructive) {
                                pendingDelete = ChoreRef(id: item.chore.id, title: item.chore.title)
                            } label: {
                                Label("Ta bort", systemImage: "trash")
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 0)
    }

    // MARK: - Week

    private func weekList(_ chores: DailyChoreRepositoryIOS.Chores) -> some View {
        // Built from every chore the member has, not from today's list. Android filters
        // the week out of the day, so Monday's card can only ever show chores that also
        // happen to fall today — which on a Sunday leaves most of the week empty.
        let completed = Set(chores.today.filter(\.completed).map(\.chore.id))
        return ScrollView {
            LazyVStack(spacing: 10) {
                ForEach(ChoreWeekday.currentWeekDates(), id: \.self) { day in
                    WeekDayCard(
                        day: day,
                        chores: chores.all,
                        completedToday: completed
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 24)
        }
    }

    // MARK: - Small pieces

    private func emptyCard(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(palette.inkSoft)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
            )
    }

    private func noticeBanner(_ message: String) -> some View {
        HStack(spacing: 8) {
            Text(message)
                .font(.footnote)
                .foregroundStyle(palette.danger)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button {
                notice = nil
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(palette.danger)
                    .frame(width: 36, height: 36)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stäng meddelandet")
        }
        .padding(.leading, 12)
        .padding(.trailing, 2)
        .padding(.vertical, 2)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.warnBg)
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
    }

    private func errorState(_ message: String) -> some View {
        VStack(spacing: 12) {
            Text(message)
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.ink)
            Button("Försök igen") {
                Task { await load() }
            }
            .foregroundStyle(palette.accent)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// `confirmationDialog` wants a Bool binding beside the value it presents; dismissing
    /// it by any other route than the two buttons has to clear the value too.
    private var deleteDialogIsPresented: Binding<Bool> {
        Binding(
            get: { pendingDelete != nil },
            set: { presented in
                if !presented { pendingDelete = nil }
            }
        )
    }

    // MARK: - Loading and actions

    private func loadIfNeeded() async {
        if let preloaded {
            chores = preloaded
            isLoading = false
            return
        }
        guard chores == nil else { return }
        await load()
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            chores = try await DailyChoreRepositoryIOS.fetchChores(memberId: childId)
        } catch {
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte ladda uppgifter.")
        }
        isLoading = false
    }

    private func toggle(_ item: DailyChoreWithCompletionResponseDTO) async {
        let choreId = item.chore.id
        let wasCompleted = item.completed

        // Optimistic: the circle fills under the finger and is put back if the call
        // fails. A child waiting on a round trip taps again, and the second tap undoes
        // the first.
        setCompleted(!wasCompleted, forChoreId: choreId)
        do {
            try await DailyChoreRepositoryIOS.toggleChoreCompletion(
                choreId: choreId,
                date: DailyChoreRepositoryIOS.apiDate(Date()),
                isCompleted: wasCompleted
            )
            notice = nil
        } catch {
            setCompleted(wasCompleted, forChoreId: choreId)
            notice = wasCompleted
                // The one refusal the backend makes here that is not a fault: the XP
                // this chore earned has already been fed to the pet, so it cannot be
                // taken back. Worth saying plainly rather than as an HTTP failure.
                ? "Kan inte avmarkera – all mat har redan matats till husdjuret."
                : ApiErrors.message(error, fallback: "Kunde inte markera sysslan.")
        }
    }

    private func setCompleted(_ completed: Bool, forChoreId choreId: String) {
        guard let current = chores else { return }
        chores = DailyChoreRepositoryIOS.Chores(
            today: current.today.map { item in
                guard item.chore.id == choreId else { return item }
                return DailyChoreWithCompletionResponseDTO(
                    chore: item.chore,
                    completed: completed,
                    completionId: item.completionId
                )
            },
            all: current.all
        )
    }

    private func delete(_ target: ChoreRef) async {
        guard let previous = chores else { return }

        // Optimistic here too: the row goes at once and comes back if the call fails,
        // which is how the web version behaves.
        chores = DailyChoreRepositoryIOS.Chores(
            today: previous.today.filter { $0.chore.id != target.id },
            all: previous.all.filter { $0.id != target.id }
        )
        do {
            try await DailyChoreRepositoryIOS.deleteChore(choreId: target.id)
            notice = nil
        } catch {
            chores = previous
            notice = ApiErrors.message(error, fallback: "Kunde inte ta bort sysslan.")
        }
    }
}

/// The chore a confirmation dialog is asking about. The DTO itself is neither
/// Identifiable nor Equatable, and the dialog needs no more than these two fields.
private struct ChoreRef: Identifiable, Equatable {
    let id: String
    let title: String
}

extension View {
    /// A card in a plain `List`. The list is here for its swipe actions, not its chrome,
    /// so every part of that chrome is turned off in one place.
    func plainChoreRow() -> some View {
        listRowInsets(EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16))
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
    }
}

// MARK: - Chore row

struct ChoreRow: View {
    @Environment(\.seasonPalette) private var palette

    let item: DailyChoreWithCompletionResponseDTO
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 12) {
                checkbox

                VStack(alignment: .leading, spacing: 2) {
                    Text(item.chore.title)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(item.completed ? palette.inkFaint : palette.ink)
                        .strikethrough(item.completed, color: palette.inkFaint)
                        .multilineTextAlignment(.leading)

                    if item.chore.xpPoints > 0 {
                        Text("\(item.chore.xpPoints) XP")
                            .font(.caption)
                            .foregroundStyle(palette.accent)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
            )
            .shadow(color: Color.black.opacity(palette.dark ? 0.24 : 0.06), radius: 3, y: 1)
        }
        .buttonStyle(.plain)
        // The whole row is one control, so VoiceOver reads the chore and its state once
        // instead of reading a circle, a title and an XP figure as three things.
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(item.completed ? [.isButton, .isSelected] : [.isButton])
        .accessibilityHint(item.completed ? "Avmarkera" : "Markera som gjord")
    }

    private var checkbox: some View {
        ZStack {
            Circle()
                .fill(item.completed ? palette.goodInk : Color.clear)
            Circle()
                .strokeBorder(item.completed ? palette.goodInk : palette.track, lineWidth: 2)
            if item.completed {
                Image(systemName: "checkmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(palette.pageBg)
            }
        }
        .frame(width: 24, height: 24)
    }
}

// MARK: - Week

/// One day of the current week, and what recurs on it.
///
/// Only today can say whether a chore is done — the backend keeps completions per date
/// and this screen reads one date. The other six days are a schedule, and are drawn as
/// one: no circles to tick, no green.
private struct WeekDayCard: View {
    @Environment(\.seasonPalette) private var palette

    let day: Date
    let chores: [DailyChoreResponseDTO]
    let completedToday: Set<String>

    private var weekday: ChoreWeekday { ChoreWeekday.of(day) }

    private var isToday: Bool {
        Calendar.current.isDateInToday(day)
    }

    private var scheduled: [DailyChoreResponseDTO] {
        chores.filter { $0.isActive && $0.weekdays.contains(weekday.code) }
    }

    private var doneCount: Int {
        scheduled.filter { completedToday.contains($0.id) }.count
    }

    private var allDone: Bool {
        isToday && !scheduled.isEmpty && doneCount == scheduled.count
    }

    private var dateLabel: String {
        let calendar = Calendar.current
        let dayOfMonth = calendar.component(.day, from: day)
        let month = calendar.component(.month, from: day)
        return "\(weekday.short) \(dayOfMonth)/\(month)"
    }

    private var countLabel: String {
        if scheduled.isEmpty { return "–" }
        if isToday { return "\(doneCount)/\(scheduled.count)" }
        return scheduled.count == 1 ? "1 syssla" : "\(scheduled.count) sysslor"
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            choreList(for: scheduled)
        }
        .frame(maxWidth: .infinity)
        .background(isToday ? palette.calBg : palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(isToday ? palette.accent : Color.clear, lineWidth: 2)
        )
        .shadow(color: Color.black.opacity(palette.dark ? 0.2 : 0.05), radius: isToday ? 4 : 2, y: 1)
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text(dateLabel)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(isToday ? palette.accent : palette.ink)

            if isToday {
                Text("idag")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(palette.onAccent)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous).fill(palette.accent)
                    )
            }

            Spacer(minLength: 8)

            Text(countLabel)
                .font(.footnote.weight(.medium))
                .foregroundStyle(allDone ? palette.goodInk : palette.inkSoft)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(isToday ? palette.calBg : palette.tipBg)
    }

    @ViewBuilder
    private func choreList(for scheduled: [DailyChoreResponseDTO]) -> some View {
        if scheduled.isEmpty {
            Text("Inga sysslor")
                .font(.footnote)
                .foregroundStyle(palette.inkFaint)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
        } else {
            VStack(spacing: 6) {
                ForEach(scheduled, id: \.id) { chore in
                    row(for: chore)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
    }

    private func row(for chore: DailyChoreResponseDTO) -> some View {
        let done = isToday && completedToday.contains(chore.id)
        return HStack(spacing: 8) {
            // A filled dot for a day that is only a plan, a real check for the one day
            // that has an answer. Android draws ✅ and ⭕ here, which are two emoji
            // pretending to be one control's two states.
            Image(systemName: isToday ? (done ? "checkmark.circle.fill" : "circle") : "circle.fill")
                .font(.system(size: isToday ? 14 : 6))
                .foregroundStyle(isToday ? (done ? palette.goodInk : palette.track) : palette.inkFaint)
                .frame(width: 16)

            Text(chore.title)
                .font(.footnote)
                .foregroundStyle(done ? palette.inkFaint : palette.inkSoft)
                .strikethrough(done, color: palette.inkFaint)
                .frame(maxWidth: .infinity, alignment: .leading)

            if chore.xpPoints > 0 {
                Text("\(chore.xpPoints) XP")
                    .font(.caption2)
                    .foregroundStyle(palette.inkFaint)
            }
        }
    }
}

// MARK: - Weekdays

/// The week as the backend spells it, as Sweden reads it, and as a chip can show it.
struct ChoreWeekday: Identifiable, Hashable {
    /// 0 = Monday … 6 = Sunday, which is the order a Swedish week is read in — not the
    /// order `Calendar` numbers its weekdays.
    let index: Int
    /// The code the daily-chore endpoints use.
    let code: String
    let short: String
    let initial: String
    /// Swedish, and every one of them takes -ar in the plural: "söndagar".
    let full: String

    var id: Int { index }

    static let monday = ChoreWeekday(index: 0, code: "MON", short: "Mån", initial: "M", full: "måndag")

    static let all: [ChoreWeekday] = [
        monday,
        ChoreWeekday(index: 1, code: "TUE", short: "Tis", initial: "T", full: "tisdag"),
        ChoreWeekday(index: 2, code: "WED", short: "Ons", initial: "O", full: "onsdag"),
        ChoreWeekday(index: 3, code: "THU", short: "Tor", initial: "T", full: "torsdag"),
        ChoreWeekday(index: 4, code: "FRI", short: "Fre", initial: "F", full: "fredag"),
        ChoreWeekday(index: 5, code: "SAT", short: "Lör", initial: "L", full: "lördag"),
        ChoreWeekday(index: 6, code: "SUN", short: "Sön", initial: "S", full: "söndag"),
    ]

    static func of(_ date: Date, calendar: Calendar = .current) -> ChoreWeekday {
        // Calendar numbers Sunday 1 … Saturday 7 whatever the locale's first weekday is,
        // so it is converted rather than trusted to start the week on Monday.
        let index = (calendar.component(.weekday, from: date) + 5) % 7
        // The modulo lands inside `all`; the fallback is only here so this needs no
        // force unwrap.
        return all.first { $0.index == index } ?? monday
    }

    /// Monday through Sunday of the week `today` falls in.
    static func currentWeekDates(from today: Date = Date(), calendar: Calendar = .current) -> [Date] {
        let start = calendar.startOfDay(for: today)
        guard let monday = calendar.date(byAdding: .day, value: -of(today, calendar: calendar).index, to: start) else {
            return [start]
        }
        return (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: monday) }
    }
}

// MARK: - New chore

/// One sheet for both of Android's two dialogs.
///
/// Android has "+ Idag" and "🔁 Återkommande" as a pinned pair of filled buttons, which
/// opens two dialogs that differ by one row. On iOS that pair would hold 72pt of every
/// screenful for an action used a few times a month, so it is a "+" in the header bar
/// and the difference between the two is a choice inside the sheet.
private struct AddChoreSheet: View {
    @Environment(\.seasonPalette) private var palette
    @Environment(\.dismiss) private var dismiss

    let childName: String
    let childId: String
    let onCreated: () -> Void

    @State private var title = ""
    @State private var repeatsWeekly = false
    @State private var weekdays: Set<Int> = []
    @State private var xpPoints = 1
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var trimmedTitle: String {
        title.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Titel", text: $title)
                        .textInputAutocapitalization(.sentences)
                }

                Section {
                    Picker("När", selection: $repeatsWeekly) {
                        Text("Bara idag").tag(false)
                        Text("Varje vecka").tag(true)
                    }
                    .pickerStyle(.segmented)

                    if repeatsWeekly {
                        weekdayChips
                    }
                } footer: {
                    // Worth saying, because the API has no one-off chore: "bara idag" is
                    // a chore on today's weekday, and it comes back next week unless it
                    // is deleted. Android says nothing about this at all.
                    if !repeatsWeekly {
                        Text("Läggs på \(ChoreWeekday.of(Date()).full)ar och kommer tillbaka nästa vecka om den inte tas bort.")
                    }
                }

                Section("XP (mat)") {
                    Picker("XP", selection: $xpPoints) {
                        Text("1 XP").tag(1)
                        Text("2 XP").tag(2)
                        Text("3 XP").tag(3)
                    }
                    .pickerStyle(.segmented)
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(palette.danger)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(palette.pageBg.ignoresSafeArea())
            .navigationTitle("Ny syssla – \(childName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Spara") {
                        Task { await save() }
                    }
                    .disabled(isSaving || trimmedTitle.isEmpty)
                }
            }
        }
    }

    private var weekdayChips: some View {
        HStack(spacing: 6) {
            ForEach(ChoreWeekday.all) { day in
                let selected = weekdays.contains(day.index)
                Button {
                    if selected {
                        weekdays.remove(day.index)
                    } else {
                        weekdays.insert(day.index)
                    }
                } label: {
                    Text(day.initial)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(selected ? palette.onAccent : palette.calInk)
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(
                            RoundedRectangle(cornerRadius: 9, style: .continuous)
                                .fill(selected ? palette.accent : palette.calBg)
                        )
                }
                .buttonStyle(.plain)
                // The initials repeat (T for tisdag and torsdag, S for söndag), so the
                // chip's label alone is not something VoiceOver can act on.
                .accessibilityLabel(day.short)
                .accessibilityAddTraits(selected ? [.isButton, .isSelected] : [.isButton])
            }
        }
        .padding(.vertical, 2)
    }

    private func save() async {
        guard !trimmedTitle.isEmpty else {
            errorMessage = "Fyll i en titel."
            return
        }

        let days: [String]
        if repeatsWeekly {
            guard !weekdays.isEmpty else {
                errorMessage = "Välj minst en veckodag."
                return
            }
            days = ChoreWeekday.all.filter { weekdays.contains($0.index) }.map(\.code)
        } else {
            days = [ChoreWeekday.of(Date()).code]
        }

        isSaving = true
        errorMessage = nil
        do {
            try await DailyChoreRepositoryIOS.createChore(
                memberId: childId,
                title: trimmedTitle,
                weekdays: days,
                xpPoints: xpPoints
            )
            onCreated()
            dismiss()
        } catch {
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte skapa sysslan.")
        }
        isSaving = false
    }
}

// MARK: - Fixture

#if DEBUG
extension ChildTasksView {

    /// The screen with sample data and no session, so it can be photographed.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen behind
    /// a login cannot be reached to be looked at at all. This is the way in — see
    /// ScreenHarness in KidQuestApp.swift.
    ///
    /// The chores are deliberately mixed: two already done, one worth more than one XP,
    /// and two that do not fall on every day — so the Idag tab shows both row states and
    /// the Vecka tab shows a week that is not seven identical cards.
    static func fixture(tab: Tab = .today) -> ChildTasksView {
        let everyDay = ChoreWeekday.all.map(\.code)
        let todayCode = ChoreWeekday.of(Date()).code

        let all: [DailyChoreResponseDTO] = [
            fixtureChore(id: "c1", title: "Borsta håret", weekdays: everyDay, xp: 1),
            fixtureChore(id: "c2", title: "Klippa naglar", weekdays: ["SUN"], xp: 1),
            fixtureChore(id: "c3", title: "Städa lekrum", weekdays: everyDay, xp: 1),
            fixtureChore(id: "c4", title: "Borsta tänderna morgon och kväll", weekdays: everyDay, xp: 2),
            fixtureChore(id: "c5", title: "Häng upp ytterkläder", weekdays: everyDay, xp: 1),
            fixtureChore(id: "c6", title: "Städa sovrum", weekdays: ["MON", "WED", "FRI"], xp: 1),
            fixtureChore(id: "c7", title: "Inga leksaker på övervåningen", weekdays: everyDay, xp: 1),
            fixtureChore(id: "c8", title: "Ställ undan disk", weekdays: everyDay, xp: 1),
        ]

        // Today's list is derived from the schedule rather than written out twice, so
        // the two tabs cannot contradict each other on whatever day this is run.
        let today = all
            .filter { $0.weekdays.contains(todayCode) }
            .map { chore in
                DailyChoreWithCompletionResponseDTO(
                    chore: chore,
                    completed: ["c1", "c3"].contains(chore.id),
                    completionId: nil
                )
            }

        return ChildTasksView(
            childName: "Signe",
            childId: "child-1",
            preloaded: DailyChoreRepositoryIOS.Chores(today: today, all: all),
            initialTab: tab
        )
    }

    private static func fixtureChore(
        id: String,
        title: String,
        weekdays: [String],
        xp: Int
    ) -> DailyChoreResponseDTO {
        DailyChoreResponseDTO(
            id: id,
            memberId: "child-1",
            title: title,
            weekdays: weekdays,
            xpPoints: xp,
            isActive: true
        )
    }
}

#Preview("Barnets sysslor") {
    ChildTasksView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Barnets sysslor – vecka") {
    ChildTasksView.fixture(tab: .week)
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Barnets sysslor mörk") {
    ChildTasksView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: true))
        .preferredColorScheme(.dark)
}
#endif
