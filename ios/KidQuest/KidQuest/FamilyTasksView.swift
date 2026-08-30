import SwiftUI

/// The whole family's day, one section per child.
///
/// A port of the Android FamilyTasksScreen, reached from the seasonal band on the
/// parent's overview ("Alla uppgifter ›"). It is the same material as `ChildTasksView`
/// at one step back: the same header bar, the same Idag/Vecka pair, the same rows —
/// deliberately, because the two screens sit next to each other in the app.
///
/// Two things Android does here are not carried over, and both are marked where they
/// happen: emoji in the tab labels, and a week built by filtering today's list, which
/// leaves most weekday cards empty on any day that is not Monday.
///
/// A child never arrives here — the route is offered by the parent's overview, which
/// children are kept out of at startup. There is nothing parent-only to gate inside
/// the screen either: it offers no way to add or delete a chore, only to tick one off,
/// and ticking is open to whoever holds the phone on the server as well as here.
struct FamilyTasksView: View {
    var onBack: () -> Void = {}

    /// Non-nil renders this family instead of calling the network. Only `fixture()`
    /// sets it; it stays a plain stored property rather than living behind `#if DEBUG`
    /// so the memberwise initialiser has the same shape in both configurations.
    var preloaded: FamilyTasksRepository.Family?

    /// Which tab the screen opens on. Only the fixture sets it: the simulator takes no
    /// touch input, so a tab that cannot be tapped cannot otherwise be photographed.
    var initialTab: ChoreTab?

    @Environment(\.seasonPalette) private var palette

    @State private var family: FamilyTasksRepository.Family?
    @State private var isLoading = true
    @State private var errorMessage: String?
    /// A failed tick, said out loud above the list. Not the same thing as
    /// `errorMessage`: the list is still good, one action on it was not.
    @State private var notice: String?
    @State private var chosenTab: ChoreTab?

    private var tab: ChoreTab { chosenTab ?? initialTab ?? .today }

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(
                title: "Familjens uppgifter",
                subtitle: subtitle,
                onBack: onBack
            )

            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let errorMessage {
                errorState(errorMessage)
            } else if let family {
                ChoreTabPicker(selected: tab) { chosenTab = $0 }

                if let notice {
                    noticeBanner(notice)
                }

                switch tab {
                case .today:
                    todayList(family)
                case .week:
                    weekList(family)
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
    }

    // MARK: - Header

    /// "söndag 30/8", and once the family has loaded, how much of it is done.
    ///
    /// The family total is the one thing this screen has that the child's does not,
    /// and the bar already has the line free. Per-child counts stay on the sections.
    private var subtitle: String {
        guard let family, family.totalToday > 0 else { return todayLabel }
        return "\(todayLabel) · \(family.doneToday)/\(family.totalToday) gjorda"
    }

    private var todayLabel: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "sv_SE")
        formatter.dateFormat = "EEEE d/M"
        return formatter.string(from: Date())
    }

    // MARK: - Today

    /// Android draws a card per child with the chores nested inside it. Here the child
    /// is a section header instead, so the rows are literally the rows of the child's
    /// own screen and the name stays pinned to the top while a parent scrolls twelve
    /// chores — which the nested card cannot do, since its heading scrolls away with it.
    private func todayList(_ family: FamilyTasksRepository.Family) -> some View {
        List {
            if family.children.isEmpty {
                emptyCard("Inga barn i familjen än.")
                    .plainChoreRow()
            } else {
                ForEach(family.children) { child in
                    Section {
                        childRows(child)
                    } header: {
                        childHeader(child)
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 0)
    }

    @ViewBuilder
    private func childRows(_ child: FamilyTasksRepository.ChildChores) -> some View {
        if child.loadFailed {
            emptyCard("Kunde inte läsa \(child.name)s sysslor.")
                .plainChoreRow()
        } else if child.today.isEmpty {
            emptyCard("Inga sysslor idag.")
                .plainChoreRow()
        } else {
            ForEach(child.today, id: \.chore.id) { item in
                // The same row as the child's own screen, not a copy of it. Deleting is
                // not offered here, so there is no swipe action: a chore is removed on
                // the child's own list, where the parent can see the whole schedule.
                ChoreRow(item: item) {
                    Task { await toggle(item, childId: child.id) }
                }
                .plainChoreRow()
            }
        }
    }

    private func childHeader(_ child: FamilyTasksRepository.ChildChores) -> some View {
        HStack(spacing: 8) {
            Text(child.name)
                .font(.headline)
                .foregroundStyle(palette.ink)

            Spacer(minLength: 8)

            Text(countLabel(child))
                .font(.caption.weight(.semibold))
                .foregroundStyle(child.allDone ? palette.goodInk : palette.inkSoft)
                .padding(.horizontal, 10)
                .padding(.vertical, 3)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(child.allDone ? palette.goodBg : palette.tipBg)
                )
        }
        .padding(.top, 10)
        .padding(.bottom, 6)
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        // The header sticks while its section scrolls, so it needs the page's own
        // ground behind it — otherwise the rows show through it on the way past. Both
        // the row background and the header's own: a plain list gives its pinned
        // headers a blur of their own, and only the inner one is certain to win.
        .background(palette.pageBg)
        .listRowInsets(EdgeInsets())
        .listRowBackground(palette.pageBg)
        .listRowSeparator(.hidden)
        // A plain list upper-cases section headers by default, which would shout a
        // child's name at a parent.
        .textCase(nil)
        .accessibilityElement(children: .combine)
    }

    private func countLabel(_ child: FamilyTasksRepository.ChildChores) -> String {
        if child.loadFailed { return "Kunde inte läsas" }
        if child.total == 0 { return "Inga sysslor idag" }
        if child.allDone { return "Allt klart (\(child.total))" }
        return "\(child.done) / \(child.total) gjorda"
    }

    // MARK: - Week

    private func weekList(_ family: FamilyTasksRepository.Family) -> some View {
        // Built from every chore each child has, not from today's list. Android filters
        // the week out of the day, so Monday's card can only ever show chores that also
        // happen to fall today — which on a Sunday leaves most of the week empty.
        ScrollView {
            LazyVStack(spacing: 10) {
                ForEach(ChoreWeekday.currentWeekDates(), id: \.self) { day in
                    FamilyWeekDayCard(day: day, children: family.children)
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

    // MARK: - Loading and actions

    private func loadIfNeeded() async {
        if let preloaded {
            family = preloaded
            isLoading = false
            return
        }
        guard family == nil else { return }
        await load()
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            family = try await FamilyTasksRepository.fetchFamilyChores()
        } catch {
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte ladda uppgifter.")
        }
        isLoading = false
    }

    private func toggle(_ item: DailyChoreWithCompletionResponseDTO, childId: String) async {
        let choreId = item.chore.id
        let wasCompleted = item.completed

        // Optimistic: the circle fills under the finger and is put back if the call
        // fails. A parent waiting on a round trip taps again, and the second tap undoes
        // the first.
        setCompleted(!wasCompleted, choreId: choreId, childId: childId)
        do {
            try await DailyChoreRepositoryIOS.toggleChoreCompletion(
                choreId: choreId,
                date: DailyChoreRepositoryIOS.apiDate(Date()),
                isCompleted: wasCompleted
            )
            notice = nil
        } catch {
            setCompleted(wasCompleted, choreId: choreId, childId: childId)
            notice = wasCompleted
                // The one refusal the backend makes here that is not a fault: the XP
                // this chore earned has already been fed to the pet, so it cannot be
                // taken back. Worth saying plainly rather than as an HTTP failure.
                ? "Kan inte avmarkera – all mat har redan matats till husdjuret."
                : ApiErrors.message(error, fallback: "Kunde inte markera sysslan.")
        }
    }

    /// Scoped by child as well as by chore. Chore ids are unique across the family, but
    /// the screen holds several children at once and a rewrite that only matched on the
    /// chore would be one backend change away from ticking two rows.
    private func setCompleted(_ completed: Bool, choreId: String, childId: String) {
        guard var current = family else { return }
        current.children = current.children.map { child in
            guard child.id == childId else { return child }
            var updated = child
            updated.today = child.today.map { item in
                guard item.chore.id == choreId else { return item }
                return DailyChoreWithCompletionResponseDTO(
                    chore: item.chore,
                    completed: completed,
                    completionId: item.completionId
                )
            }
            return updated
        }
        family = current
    }
}

// MARK: - Week

/// One day of the current week, and what every child has scheduled on it.
///
/// Only today can say whether a chore is done — the backend keeps completions per date
/// and this screen reads one date. The other six days are a schedule, and are drawn as
/// one: no circles to tick, no green.
///
/// A near-twin of the card on `ChildTasksView`, and kept separate rather than shared
/// because the two hold different shapes: one day of one child's chores against one day
/// of several children's, each under their own name. The chrome is the same by
/// construction — same corner radius, same palette roles, same "idag" pill.
private struct FamilyWeekDayCard: View {
    @Environment(\.seasonPalette) private var palette

    let day: Date
    let children: [FamilyTasksRepository.ChildChores]

    /// One child's share of this day. Empty shares are dropped before this is built, so
    /// a day only names the children who actually have something on it.
    private struct Share: Identifiable {
        let id: String
        let name: String
        let chores: [DailyChoreResponseDTO]
        let completed: Set<String>
    }

    private var weekday: ChoreWeekday { ChoreWeekday.of(day) }

    private var isToday: Bool {
        Calendar.current.isDateInToday(day)
    }

    private var shares: [Share] {
        children.compactMap { child in
            let scheduled = child.all.filter { $0.isActive && $0.weekdays.contains(weekday.code) }
            guard !scheduled.isEmpty else { return nil }
            return Share(
                id: child.id,
                name: child.name,
                chores: scheduled,
                completed: Set(child.today.filter(\.completed).map(\.chore.id))
            )
        }
    }

    private func total(_ shares: [Share]) -> Int {
        shares.reduce(0) { $0 + $1.chores.count }
    }

    /// Nothing but today has an answer, so no other day counts anything as done.
    private func done(_ shares: [Share]) -> Int {
        guard isToday else { return 0 }
        return shares.reduce(0) { sum, share in
            sum + share.chores.filter { share.completed.contains($0.id) }.count
        }
    }

    private var dateLabel: String {
        let calendar = Calendar.current
        let dayOfMonth = calendar.component(.day, from: day)
        let month = calendar.component(.month, from: day)
        return "\(weekday.short) \(dayOfMonth)/\(month)"
    }

    private func countLabel(total: Int, done: Int) -> String {
        if total == 0 { return "–" }
        if isToday { return "\(done)/\(total)" }
        return total == 1 ? "1 syssla" : "\(total) sysslor"
    }

    var body: some View {
        // Worked out once and handed down: every count on the card reads the same list,
        // and `shares` filters every child's schedule each time it is asked.
        let shares = self.shares
        return VStack(spacing: 0) {
            header(shares)
            choreList(for: shares)
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

    private func header(_ shares: [Share]) -> some View {
        let total = self.total(shares)
        let done = self.done(shares)
        let allDone = isToday && total > 0 && done == total
        return HStack(spacing: 8) {
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

            Text(countLabel(total: total, done: done))
                .font(.footnote.weight(.medium))
                .foregroundStyle(allDone ? palette.goodInk : palette.inkSoft)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(isToday ? palette.calBg : palette.tipBg)
    }

    @ViewBuilder
    private func choreList(for shares: [Share]) -> some View {
        if shares.isEmpty {
            Text("Inga sysslor")
                .font(.footnote)
                .foregroundStyle(palette.inkFaint)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
        } else {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(shares) { share in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(share.name)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(palette.inkSoft)

                        ForEach(share.chores, id: \.id) { chore in
                            row(chore, done: isToday && share.completed.contains(chore.id))
                        }
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
        }
    }

    private func row(_ chore: DailyChoreResponseDTO, done: Bool) -> some View {
        HStack(spacing: 8) {
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
        .padding(.leading, 4)
    }
}

// MARK: - Fixture

#if DEBUG
extension FamilyTasksView {

    /// The screen with sample data and no session, so it can be photographed.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen behind
    /// a login cannot be reached to be looked at at all. This is the way in — see
    /// ScreenHarness in KidQuestApp.swift.
    ///
    /// The three children are deliberately different shapes, because the section header
    /// has three states and one family has to show all of them: part-way through the
    /// day, finished, and nothing scheduled at all.
    static func fixture(tab: ChoreTab = .today) -> FamilyTasksView {
        FamilyTasksView(
            preloaded: FamilyTasksRepository.Family(children: [
                child(
                    id: "child-1",
                    name: "Signe",
                    completed: ["c1", "c3"],
                    chores: [
                        (id: "c1", title: "Borsta håret", weekdays: everyDay, xp: 1),
                        (id: "c2", title: "Klippa naglar", weekdays: ["SUN"], xp: 1),
                        (id: "c3", title: "Städa lekrum", weekdays: everyDay, xp: 1),
                        (id: "c4", title: "Borsta tänderna morgon och kväll", weekdays: everyDay, xp: 2),
                        (id: "c5", title: "Häng upp ytterkläder", weekdays: everyDay, xp: 1),
                        (id: "c6", title: "Städa sovrum", weekdays: ["MON", "WED", "FRI"], xp: 1),
                    ]
                ),
                child(
                    id: "child-2",
                    name: "Walter",
                    // Every one of Walter's ticked, so the "Allt klart" badge and the
                    // struck-through row are both in the picture.
                    completed: ["w1", "w2", "w3", "w4"],
                    chores: [
                        (id: "w1", title: "Inga leksaker på övervåningen", weekdays: everyDay, xp: 1),
                        (id: "w2", title: "Städa sovrum", weekdays: everyDay, xp: 1),
                        (id: "w3", title: "Borsta tänderna morgon/kväll", weekdays: everyDay, xp: 1),
                        (id: "w4", title: "Städa lekrummet", weekdays: ["SAT", "SUN"], xp: 2),
                    ]
                ),
                // No chores at all: the third state the header has to hold.
                FamilyTasksRepository.ChildChores(
                    id: "child-3",
                    name: "Ester",
                    today: [],
                    all: [],
                    loadFailed: false
                ),
            ]),
            initialTab: tab
        )
    }

    private static var everyDay: [String] { ChoreWeekday.all.map(\.code) }

    /// Today's list is derived from the schedule rather than written out twice, so the
    /// two tabs cannot contradict each other on whatever day this is run.
    private static func child(
        id: String,
        name: String,
        completed: Set<String>,
        chores: [(id: String, title: String, weekdays: [String], xp: Int)]
    ) -> FamilyTasksRepository.ChildChores {
        let all = chores.map { chore in
            DailyChoreResponseDTO(
                id: chore.id,
                memberId: id,
                title: chore.title,
                weekdays: chore.weekdays,
                xpPoints: chore.xp,
                isActive: true
            )
        }
        let todayCode = ChoreWeekday.of(Date()).code
        let today = all
            .filter { $0.weekdays.contains(todayCode) }
            .map { chore in
                DailyChoreWithCompletionResponseDTO(
                    chore: chore,
                    completed: completed.contains(chore.id),
                    completionId: nil
                )
            }
        return FamilyTasksRepository.ChildChores(
            id: id,
            name: name,
            today: today,
            all: all,
            loadFailed: false
        )
    }
}

#Preview("Familjens uppgifter") {
    FamilyTasksView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Familjens uppgifter – vecka") {
    FamilyTasksView.fixture(tab: .week)
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Familjens uppgifter mörk") {
    FamilyTasksView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: true))
        .preferredColorScheme(.dark)
}
#endif
