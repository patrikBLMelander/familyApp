import SwiftUI

/// The child's own screen, seen from a parent's phone — "Visa som barn".
///
/// It is a separate view rather than a flag on `ChildDashboardView`, and the reason is
/// worth stating: that screen reads `pets/current`, `xp/current`, `pets/collected-food`
/// and `wallet/balance`, all of which resolve the member from the device token, and it
/// feeds and picks eggs through the same token-scoped routes. Handing it a `childId`
/// changes nothing about which data it shows — a parent would see their OWN empty pet
/// and, worse, "Mata allt" would pour the child's food into it. Every read and every
/// write here goes through `MemberScopedRepository`, which names the child in the path.
///
/// Android solved the same problem with an `actingAsParent` flag inside
/// `ChildDashboardScreen`; iOS keeps the two apart because the child's screen is not
/// ours to edit, and because a screen where every call has one meaning is easier to
/// keep honest than one where every call has two.
///
/// The amber banner is load-bearing, not decoration: a parent who puts the phone down
/// mid-task and picks it up again has no other way to tell whose screen this is.
struct ChildDashboardHost: View {

    /// Which child is being looked at. Mutable because "Byt barn" swaps it in place
    /// rather than routing back out to the family overview and in again.
    struct ChildRef: Identifiable, Equatable {
        let id: String
        let name: String
    }

    let child: ChildRef
    /// "Tillbaka" — out of the child's view, back to the parent's own.
    var onExit: () -> Void = {}
    /// Both already take a member id and are already parent-safe, so they are handed
    /// the child that is currently being viewed rather than the one we opened with.
    var onOpenTasks: (ChildRef) -> Void = { _ in }
    var onOpenWallet: (ChildRef) -> Void = { _ in }

    /// Non-nil renders these values instead of calling the network. Only `fixture()`
    /// sets them; they stay plain stored properties rather than living behind `#if
    /// DEBUG` so the memberwise initialiser has the same shape in both configurations.
    var preloaded: MemberScopedRepository.Snapshot?
    var preloadedSiblings: [ChildRef]?

    @State private var viewing: ChildRef?
    @State private var snapshot: MemberScopedRepository.Snapshot?
    @State private var isLoading = true
    @State private var errorMessage: String?
    /// A single failed action above an otherwise good screen. Not `errorMessage`: the
    /// data is fine, one thing we tried to do with it was not.
    @State private var notice: String?
    @State private var isFeeding = false
    @State private var showSelectEgg = false
    @State private var hasAutoOpenedEgg = false
    @State private var farewell: MonthFarewellData?
    @State private var farewellChecked = false
    /// Förälderns vy: allt är member-scopat, inklusive XP-historiken.
    private var farewellMemberScope: String? { activeChild.id }
    @State private var showSwitchChild = false
    @State private var siblings: [ChildRef] = []
    /// Barnets tidigare djur, och vilket som visas. Samma samling barnet ser.
    @State private var history: [PetHistoryResponseDTO] = []
    @State private var viewingPast: PetHistoryResponseDTO?

    /// Icke-tom hoppar över nätanropet. Bara fixturen sätter den.
    var preloadedHistory: [PetHistoryResponseDTO] = []

    /// Dark ink on pale cards, as on the child's own screen. The page behind them is a
    /// species gradient that runs from pastel to near-black depending on the animal, so
    /// the cards bring their own light ground rather than following the seasonal
    /// palette into a colour that would vanish against dragon or panda.
    private let cardInk = Color(red: 28 / 255, green: 25 / 255, blue: 23 / 255)
    private let cardInkSoft = Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255)

    private var activeChild: ChildRef { viewing ?? child }

    var body: some View {
        Group {
            if isLoading {
                ZStack {
                    palette.pageBg.ignoresSafeArea()
                    ProgressView().tint(palette.accent)
                }
            } else if let errorMessage {
                ZStack {
                    palette.pageBg.ignoresSafeArea()
                    errorCard(errorMessage).padding(16)
                }
            } else if let snapshot {
                layout(snapshot)
            } else {
                palette.pageBg.ignoresSafeArea()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // Ingen egen toppbar längre. Poängen med "Visa som barn" är att se det barnet
        // ser, och en rubrikrad som barnet inte har gör skärmarna olika igen. Identitet,
        // "Byt barn" och vägen ut bär den gula banderollen i stället -- den låg redan
        // där och gjorde två av de tre sakerna.
        .toolbar(.hidden, for: .navigationBar)
        .task(id: activeChild.id) {
            await loadIfNeeded()
            if !preloadedHistory.isEmpty {
                history = preloadedHistory
            } else {
                history = await MemberScopedRepository.fetchPetHistory(memberId: activeChild.id)
            }
        }
        // Sist i ZStacken: senare syskon ritar överst, och avskedet ska ligga över
        // hela barnvyn.
        .overlay {
            if let farewell {
                MonthFarewell(data: farewell, palette: palette) {
                    FarewellLog.markSeen(memberId: activeChild.id,
                                         year: farewell.entry.year,
                                         month: farewell.entry.month)
                    self.farewell = nil
                    showSelectEgg = true
                }
                .transition(.opacity)
            }
        }
        .sheet(isPresented: $showSelectEgg) {
            // The member id is what makes this the CHILD's egg and not the parent's.
            SelectEggSheet(
                memberId: activeChild.id,
                history: history,
                onDismiss: { showSelectEgg = false },
                onEggSelected: { pet in
                    snapshot?.pet = pet
                    snapshot?.petLoadFailed = false
                }
            )
        }
        .sheet(isPresented: $showSwitchChild) {
            switchChildSheet
        }
    }

    /// Samma vy barnet ser. Skillnaden är var siffrorna kommer ifrån: varje anrop här
    /// namnger barnet i sökvägen, medan barnets egen skärm läser med sin egen token.
    private func layout(_ snapshot: MemberScopedRepository.Snapshot) -> some View {
        ChildDayLayout(
            childName: activeChild.name,
            pet: snapshot.pet,
            petLoadFailed: snapshot.petLoadFailed,
            level: max(1, min(5, snapshot.xp?.currentLevel ?? 1)),
            xpInLevel: snapshot.xp?.xpInCurrentLevel ?? 0,
            xpForNext: snapshot.xp?.xpForNextLevel ?? 0,
            foodCount: snapshot.foodCount,
            balance: snapshot.balance?.balance,
            tasks: snapshot.todaysChores,
            history: history,
            viewingPast: $viewingPast,
            isFeeding: isFeeding,
            onToggleTask: { item in Task { await toggle(item) } },
            onFeed: { amount in Task { await feed(amount: amount) } },
            onOpenWallet: { onOpenWallet(activeChild) },
            onSelectEgg: { showSelectEgg = true },
            // Nil: en förälder lägger till sysslor i sin egen vy, inte härifrån.
            onAddChore: nil,
            banner: {
                VStack(spacing: 10) {
                    actingAsParentBanner
                    if let notice {
                        noticeBanner(notice)
                    }
                }
            },
            footer: { EmptyView() }
        )
    }

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    // MARK: - Chrome

    private var actingAsParentBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "eye")
                .font(.footnote.weight(.semibold))

            Text("Du ser \(possessive(activeChild.name)) vy")
                .font(.subheadline.weight(.medium))
                .frame(maxWidth: .infinity, alignment: .leading)

            Button("Byt barn") {
                showSwitchChild = true
                Task { await loadSiblings() }
            }
            .font(.subheadline.weight(.semibold))

            Button("Tillbaka", action: onExit)
                .font(.subheadline.weight(.semibold))
        }
        .foregroundStyle(Color(red: 0x78 / 255, green: 0x35 / 255, blue: 0x0F / 255))
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(red: 0xFE / 255, green: 0xF3 / 255, blue: 0xC7 / 255))
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Du tittar på \(activeChild.name)s vy som förälder")
    }

    /// Svensk genitiv: "Signes vy", men "Lukas vy" — namn som slutar på s, x eller z
    /// får inget extra s.
    private func possessive(_ name: String) -> String {
        guard let last = name.lowercased().last else { return name }
        return "sxz".contains(last) ? name : name + "s"
    }

    private func noticeBanner(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(Color(red: 0x7F / 255, green: 0x1D / 255, blue: 0x1D / 255))
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(red: 0xFE / 255, green: 0xE2 / 255, blue: 0xE2 / 255))
            )
    }

    private func errorCard(_ text: String) -> some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text(text)
                    .foregroundStyle(cardInk)
                Button("Försök igen") {
                    Task { await load() }
                }
                .buttonStyle(.borderedProminent)
            }
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content()
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.white.opacity(0.82))
            )
    }

    // MARK: - Byt barn

    private var switchChildSheet: some View {
        NavigationStack {
            List {
                if siblings.isEmpty {
                    Text("Inga andra barn i familjen.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(siblings) { sibling in
                        Button {
                            showSwitchChild = false
                            guard sibling.id != activeChild.id else { return }
                            // Reset first: leaving the previous child's pet and balance
                            // on screen under a new name is exactly the confusion this
                            // whole screen exists to avoid.
                            snapshot = nil
                            notice = nil
                            isLoading = true
                            viewing = sibling
                        } label: {
                            HStack {
                                Text(sibling.name)
                                    .foregroundStyle(.primary)
                                Spacer()
                                if sibling.id == activeChild.id {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.tint)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Byt barn")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Avbryt") { showSwitchChild = false }
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Data

    private func loadIfNeeded() async {
        // Fixtures answer for whichever child is on screen, including after "Byt barn":
        // the alternative is a preview that reaches for the network and draws an error
        // card, which is the one thing a screen kept for photographing must not do.
        if let preloaded {
            snapshot = preloaded
            siblings = preloadedSiblings ?? []
            isLoading = false
            return
        }
        await load()
    }

    private func load(showSpinner: Bool = true) async {
        if showSpinner { isLoading = true }
        errorMessage = nil
        let target = activeChild.id
        do {
            let fresh = try await MemberScopedRepository.fetchSnapshot(memberId: target)
            // "Byt barn" can land while a read is in flight; the late answer must not
            // overwrite the child now on screen.
            guard target == activeChild.id else { return }
            snapshot = fresh
            isLoading = false
            Task {
                await checkFarewell(hasPet: fresh.pet != nil, failed: fresh.petLoadFailed,
                                    memberId: activeChild.id)
            }
        } catch {
            guard target == activeChild.id else { return }
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte ladda barnvyn.")
            isLoading = false
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


    /// Avgör om månadsavskedet ska spelas.
    ///
    /// Fyra villkor, och alla fyra behövs. Djuret måste vara borta -- det är vad
    /// monthlyReset gör. Det måste finnas ett djur i historiken att ta avsked av; ett
    /// barn som aldrig valde ägg förra månaden har inget, och att fira ingenting är värre
    /// än att inte fira alls. Hämtningen måste ha lyckats, annars firar vi bort ett djur
    /// som bara inte gick att läsa. Och månaden får inte redan vara avklarad.
    @MainActor
    private func checkFarewell(hasPet: Bool, failed: Bool, memberId: String) async {
        guard !farewellChecked, !hasPet, !failed else { return }
        guard let senaste = history.first else { return }
        farewellChecked = true
        guard !FarewellLog.hasSeen(memberId: memberId,
                                   year: senaste.year, month: senaste.month) else { return }
        let tasks = await ChildDashboardRepository.fetchTasksCompleted(
            year: senaste.year, month: senaste.month,
            memberId: farewellMemberScope
        )
        farewell = MonthFarewellData(
            entry: senaste,
            petName: PetNameUtilsIOS.getPetNameSwedish(senaste.petType),
            tasks: tasks
        )
    }

    private func loadSiblings() async {
        guard preloadedSiblings == nil else { return }
        guard let children = try? await FamilyRepository.fetchChildren() else { return }
        siblings = children.map { ChildRef(id: $0.id, name: $0.name) }
    }

    /// Mata BARNETS djur. `feedPet(memberId:)` är hela poängen: `pets/feed` hade matat
    /// förälderns eget djur, som inte finns.
    private func feed(amount: Int) async {
        guard amount > 0, let current = snapshot, !isFeeding else { return }
        isFeeding = true
        notice = nil

        // Optimistic: the counter drops immediately and the pet stops looking hungry.
        snapshot?.foodCount = max(0, current.foodCount - amount)
        snapshot?.lastFedDate = DailyChoreRepositoryIOS.apiDate(Date())

        do {
            try await MemberScopedRepository.feedPet(memberId: activeChild.id, xpAmount: amount)
            await load(showSpinner: false)
        } catch {
            snapshot = current
            notice = ApiErrors.message(error, fallback: "Kunde inte ge mat just nu.")
        }
        isFeeding = false
    }

    /// Kryssa i en syssla åt barnet.
    ///
    /// This one call is NOT member-scoped, and does not need to be: the server credits
    /// the completion and its food to the chore's own member (DailyChoreService
    /// .markCompleted uses `chore.getMember()`), and only checks that the caller is in
    /// the same family. Android ticks through the same shared repository for the same
    /// reason.
    private func toggle(_ item: DailyChoreWithCompletionResponseDTO) async {
        guard let current = snapshot else { return }
        notice = nil

        snapshot?.todaysChores = current.todaysChores.map { row in
            row.chore.id == item.chore.id
                ? DailyChoreWithCompletionResponseDTO(
                    chore: row.chore,
                    completed: !row.completed,
                    completionId: row.completionId
                )
                : row
        }

        do {
            try await DailyChoreRepositoryIOS.toggleChoreCompletion(
                choreId: item.chore.id,
                date: DailyChoreRepositoryIOS.apiDate(Date()),
                isCompleted: item.completed
            )
            // Reload so the food count and XP follow the tick, without a spinner.
            await load(showSpinner: false)
        } catch {
            snapshot = current
            // Unticking is refused once the food has been eaten, and the server says so
            // in Swedish. That sentence is worth more than "något gick fel".
            notice = ApiErrors.message(error, fallback: "Kunde inte ändra sysslan.")
        }
    }
}

// MARK: - Fixture

#if DEBUG
extension ChildDashboardHost {

    /// The screen with sample data and no session, so it can be photographed.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen behind
    /// a login and an overflow menu cannot be reached to be looked at at all. This is
    /// the way in — see ScreenHarness in KidQuestApp.swift.
    ///
    /// - Parameter fed: `false` is the state a parent opens this screen to fix, so it
    ///   is the default: hungry pet, food waiting, chores half done.
    static func fixture(fed: Bool = false) -> ChildDashboardHost {
        ChildDashboardHost(
            child: ChildRef(id: "child-1", name: "Signe"),
            // Samma siffror som barnets egen skärm, ur ChildFixtures. Poängen är att de
            // två går att jämföra sida vid sida i harnesket: skiljer de sig ska det bero
            // på layouten, inte på att de fick olika data.
            preloaded: MemberScopedRepository.Snapshot(
                pet: ChildFixtures.pet,
                petLoadFailed: false,
                xp: ChildFixtures.xp,
                balance: WalletBalanceResponseDTO(id: "w1", memberId: "child-1", balance: 85),
                foodCount: fed ? 0 : 2,
                lastFedDate: fed ? DailyChoreRepositoryIOS.apiDate(Date()) : nil,
                todaysChores: ChildFixtures.tasks(allDone: false)
            ),
            preloadedSiblings: [
                ChildRef(id: "child-1", name: "Signe"),
                ChildRef(id: "child-2", name: "Walter"),
            ],
            preloadedHistory: ChildFixtures.history
        )
    }

    /// The DTOs are decode-only in production, so the fixture builds them the one way
    /// that cannot drift from the wire format: from the JSON the server actually sends.
    private static func decode<T: Decodable>(_ type: T.Type, _ json: String) -> T? {
        guard let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private static func fixturePet() -> PetResponseDTO? {
        decode(PetResponseDTO.self, """
        {"id":"p1","memberId":"child-1","year":2026,"month":8,
         "selectedEggType":"purple_egg","petType":"dragon","name":"Elden",
         "growthStage":3,"hatchedAt":"2026-08-02T10:00:00Z",
         "createdAt":"2026-08-01T08:00:00Z","updatedAt":"2026-08-20T18:00:00Z"}
        """)
    }

    private static func fixtureXp() -> XpProgressResponseDTO? {
        decode(XpProgressResponseDTO.self, """
        {"id":"x1","memberId":"child-1","year":2026,"month":8,"currentXp":52,
         "currentLevel":3,"totalTasksCompleted":41,"xpForNextLevel":18,
         "xpInCurrentLevel":17}
        """)
    }

    private static func fixtureChore(id: String, title: String, xp: Int, done: Bool) -> DailyChoreWithCompletionResponseDTO? {
        decode(DailyChoreWithCompletionResponseDTO.self, """
        {"chore":{"id":"\(id)","memberId":"child-1","title":"\(title)",
         "weekdays":["MON","TUE","WED","THU","FRI","SAT","SUN"],"xpPoints":\(xp),
         "isActive":true},"completed":\(done),"completionId":\(done ? "\"k-\(id)\"" : "null")}
        """)
    }
}

#Preview("Barnvy som förälder") {
    ChildDashboardHost.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Barnvy som förälder – matad") {
    ChildDashboardHost.fixture(fed: true)
        .environment(\.seasonPalette, SeasonTheme.current(dark: true))
        .preferredColorScheme(.dark)
}
#endif
