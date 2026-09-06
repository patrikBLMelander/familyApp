import SwiftUI

/// The parent's overview: the family's day, then the children, then the adults.
///
/// A port of the Android AdultDashboardScreen, and the decisions it arrived at are
/// carried over rather than re-litigated:
///
/// - There is no "Mina barn" heading. The cards are obviously children. "Vuxna" stays,
///   because without it the adult rows read as more children.
/// - "Lägg till familjemedlem" is the last row of the list rather than pinned to the
///   bottom of the screen. It is used a handful of times ever, and pinned it held
///   52pt of every screenful for the rest of the app's life.
/// - Each child gets one primary action. Four buttons of equal weight meant no primary
///   action at all; the chore list is what a parent opens daily, so it is the filled one.
///
/// Where the platform differs from Android it follows the platform, and says so at
/// the point where it does.
struct AdultDashboardView: View {
    var onLogout: () -> Void = {}
    var onChildPet: (String, String) -> Void = { _, _ in }
    var onChildWallet: (String, String) -> Void = { _, _ in }
    var onChildTasks: (String, String) -> Void = { _, _ in }

    /// Optional on purpose, all four of them. Android gates the paywall entry on
    /// `BillingConfig.isConfigured` -- no key, no paywall, no dead tap -- and the same
    /// reasoning covers every route iOS has not built yet. A handler that is nil hides
    /// its menu entry instead of offering a tap that goes nowhere.
    var onFamilyTasks: (() -> Void)?
    var onOpenSubscription: (() -> Void)?
    var onDeleteFamily: (() -> Void)?
    var onChildView: ((String, String) -> Void)?

    /// Non-nil renders these rows instead of calling the network. Only `fixture()`
    /// sets it; it stays a plain stored property rather than living behind `#if DEBUG`
    /// so the memberwise initialiser has the same shape in both configurations.
    var preloaded: AdultDashboardRepository.Overview?

    @Environment(\.seasonPalette) private var palette

    /// Barnlåsets kod. Sätts i barnvyns banderoll där behovet uppstår, ändras härifrån.
    @State private var parentPin: String?
    @State private var showPinSheet = false
    @State private var overview: AdultDashboardRepository.Overview?
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var inviteMember: InviteTarget?
    @State private var showAddMember = false
    @State private var settingsTarget: MemberSettingsTarget?
    @State private var showDeleteFamily = false
    @State private var onboardingDismissed = PrefsStoreIOS.isOnboardingDismissed
    /// Nil betyder "inte bestämt av användaren än", och då avgör hur långt familjen
    /// kommit om guiden visas hel eller ihopvikt.
    @State private var checklistExpanded: Bool?
    /// How far the list has scrolled, which is the only thing the top bar needs.
    @State private var scrollOffset: CGFloat = 0

    /// Height of the seasonal band. The bar overlaps its top 56pt.
    private let headerHeight: CGFloat = 196
    private let topBarHeight: CGFloat = 56

    var body: some View {
        ZStack(alignment: .top) {
            palette.pageBg.ignoresSafeArea()

            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let errorMessage {
                errorState(errorMessage)
            } else {
                list
                topBar
            }
        }
        .task { parentPin = KeychainPinStore.read() }
        .overlay {
            if showPinSheet {
                ZStack {
                    Color.black.opacity(0.55).ignoresSafeArea()
                    ParentPinSheet(
                        purpose: .change,
                        palette: palette,
                        onPinChosen: { ny in
                            showPinSheet = false
                            parentPin = ny
                            if let ny { KeychainPinStore.write(ny) } else { KeychainPinStore.delete() }
                        },
                        onDismiss: { showPinSheet = false }
                    )
                }
            }
        }
        .task {
            await loadIfNeeded()
        }
        .sheet(item: $inviteMember) { target in
            ChildInviteSheet(childName: target.name, memberId: target.id)
        }
        .sheet(isPresented: $showAddMember) {
            AddFamilyMemberSheet(onCreated: { Task { await reload() } })
        }
        .sheet(isPresented: $showDeleteFamily) {
            DeleteFamilySheet(onDeleted: { onDeleteFamily?() })
        }
        .sheet(item: $settingsTarget) { target in
            MemberSettingsSheet(
                target: target,
                onChanged: { Task { await reload() } },
                onDeleted: { Task { await reload() } }
            )
        }
    }

    // MARK: - The list

    private var list: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                SeasonHeaderBand(
                    done: overview?.doneToday ?? 0,
                    total: overview?.totalToday ?? 0,
                    height: headerHeight,
                    onTap: onFamilyTasks
                )

                if let overview, !onboardingDismissed {
                    let state = getStartedState(overview)
                    if !state.isComplete {
                        getStartedSection(state, overview: overview)
                            .padding(.horizontal, 16)
                    }
                }

                if let overview {
                    if overview.children.isEmpty {
                        emptyChildrenCard
                    } else {
                        ForEach(overview.children) { child in
                            ChildCard(
                                child: child,
                                onTasks: { onChildTasks(child.id, child.name) },
                                onPet: { onChildPet(child.id, child.name) },
                                onWallet: { onChildWallet(child.id, child.name) },
                                onInvite: { inviteMember = InviteTarget(id: child.id, name: child.name) },
                                onChildView: childViewAction(for: child),
                                onManage: {
                                    settingsTarget = MemberSettingsTarget(
                                        id: child.id,
                                        name: child.name,
                                        role: "CHILD",
                                        isCurrentUser: false
                                    )
                                }
                            )
                            .padding(.horizontal, 16)
                        }
                    }

                    if !overview.adults.isEmpty {
                        Text("Vuxna")
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(palette.ink)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)

                        ForEach(overview.adults) { adult in
                            AdultRow(
                                adult: adult,
                                onInvite: { inviteMember = InviteTarget(id: adult.id, name: adult.name) },
                                onManage: {
                                    settingsTarget = MemberSettingsTarget(
                                        id: adult.id,
                                        name: adult.name,
                                        role: adult.role,
                                        isCurrentUser: adult.isCurrentUser
                                    )
                                }
                            )
                            .padding(.horizontal, 16)
                        }
                    }
                }

                addMemberButton
                    .padding(.horizontal, 16)
                    .padding(.top, 4)

                Color.clear.frame(height: 8)
            }
            .padding(.bottom, 8)
        }
        // contentInsets is added back so the fraction starts at 0 at rest, whatever
        // safe-area inset the scroll view was given.
        .onScrollGeometryChange(for: CGFloat.self) { geometry in
            geometry.contentOffset.y + geometry.contentInsets.top
        } action: { _, offset in
            scrollOffset = offset
        }
        #if DEBUG
        // The harness opens a screen already scrolled, and a geometry change only
        // fires when something changes -- so nothing ever reported and the bar
        // photographed itself expanded, a picture of a state the app never shows.
        // This feeds the bar the same number a real scroll would, rather than drawing
        // a collapsed bar by some other route: the appearance is still computed by
        // the shipping code from a real input.
        .onAppear {
            if ProcessInfo.processInfo.environment["KQ_SCROLL"] == "bottom" {
                scrollOffset = headerHeight
            }
        }
        #endif
    }

    /// Binds one child to the caller's "visa som barn" handler, or nil when there is
    /// no such handler and the menu entry should not appear.
    private func childViewAction(for child: AdultDashboardRepository.Child) -> (() -> Void)? {
        guard let onChildView else { return nil }
        return { onChildView(child.id, child.name) }
    }

    /// How far the band has scrolled away, 0 to 1.
    private var collapsed: Double {
        let travel = headerHeight - topBarHeight
        guard travel > 0 else { return 1 }
        return min(max(Double(scrollOffset / travel), 0), 1)
    }

    private var topBar: some View {
        DashboardTopBar(
            familyName: overview?.familyName ?? AdultDashboardRepository.defaultFamilyName,
            collapsed: collapsed,
            height: topBarHeight,
            onOpenSubscription: onOpenSubscription,
            onLogout: onLogout,
            onDeleteFamily: onDeleteFamily,
            onOpenDeleteFamily: { showDeleteFamily = true },
            hasParentPin: parentPin != nil,
            onChangePin: { showPinSheet = true }
        )
    }

    private var emptyChildrenCard: some View {
        VStack(spacing: 4) {
            Text("Inga barn i familjen ännu")
                .font(.body)
                .foregroundStyle(palette.ink)
            Text("Lägg till ditt första barn nedan.")
                .font(.subheadline)
                .foregroundStyle(palette.inkSoft)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.surface)
        )
        .padding(.horizontal, 16)
    }

    private var addMemberButton: some View {
        Button { showAddMember = true } label: {
            HStack(spacing: 8) {
                Image(systemName: "plus")
                    .font(.system(size: 17, weight: .semibold))
                Text("Lägg till familjemedlem")
                    .font(.body.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .foregroundStyle(palette.accent)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(palette.accent, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
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

    // MARK: - Loading

    private func getStartedState(
        _ overview: AdultDashboardRepository.Overview
    ) -> GetStartedState {
        GetStartedState(
            hasChild: !overview.children.isEmpty,
            hasChores: overview.children.contains { $0.todaysTotal > 0 },
            hasPairedDevice: overview.children.contains { $0.hasPairedDevice },
            hasPet: overview.children.contains { $0.petType != nil }
        )
    }

    @ViewBuilder
    private func getStartedSection(
        _ state: GetStartedState,
        overview: AdultDashboardRepository.Overview
    ) -> some View {
        let expanded = checklistExpanded ?? (state.doneCount == 0)
        if expanded {
            GetStartedCard(
                state: state,
                onAddChild: { showAddMember = true },
                onAddChores: {
                    if let first = overview.children.first {
                        onChildTasks(first.id, first.name)
                    }
                },
                onPairDevice: {
                    if let first = overview.children.first {
                        inviteMember = InviteTarget(id: first.id, name: first.name)
                    }
                },
                // Barnets vy, inte djurskärmen. Djurskärmen är läsbar men inte
                // handlingsbar för en förälder -- den säger att barnet inte valt ägg
                // och erbjuder ingen väg att göra det. Äggväljaren bor i barnets vy.
                onSeePet: {
                    if let first = overview.children.first, let onChildView {
                        onChildView(first.id, first.name)
                    }
                },
                onCollapse: { checklistExpanded = false },
                onDismiss: {
                    onboardingDismissed = true
                    PrefsStoreIOS.isOnboardingDismissed = true
                }
            )
        } else {
            GetStartedStrip(state: state, onExpand: { checklistExpanded = true })
        }
    }

    private func loadIfNeeded() async {
        if let preloaded {
            overview = preloaded
            isLoading = false
            return
        }
        guard overview == nil else { return }
        await load()
    }

    /// Hämtar om utan att slå på laddningsläget. Ett blad som just stängts ska inte
    /// lämna efter sig en helskärmssnurra där listan nyss låg.
    private func reload() async {
        do {
            overview = try await AdultDashboardRepository.fetchOverview()
            errorMessage = nil
        } catch {
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte ladda familjemedlemmar.")
        }
    }

    private func load() async {
        isLoading = true
        errorMessage = nil
        do {
            overview = try await AdultDashboardRepository.fetchOverview()
        } catch {
            errorMessage = ApiErrors.message(error, fallback: "Kunde inte ladda familjemedlemmar.")
        }
        isLoading = false
    }
}

/// Identifies whose invite sheet to show. `sheet(item:)` needs an Identifiable, and a
/// bare member id would make the sheet re-present itself for the same person.
private struct InviteTarget: Identifiable, Equatable {
    let id: String
    let name: String
}

// MARK: - Header band

/// The season as a colour field, with the family's day on top of it.
///
/// The seasonal artwork went here first, at full size, and it read as clutter: the
/// paintings are detailed, and detail directly above a list of children competes with
/// them. What survived is the colour, which is what carried the season anyway. The
/// paintings stay where they started, behind each pet portrait, so the season still
/// appears twice on the screen.
///
/// It scrolls with the list. Only the bar above it is pinned.
private struct SeasonHeaderBand: View {
    @Environment(\.seasonPalette) private var palette

    let done: Int
    let total: Int
    let height: CGFloat
    /// Nil when the caller has nowhere to send a tap, in which case the band is inert
    /// and the "Alla uppgifter" affordance is not drawn at all.
    let onTap: (() -> Void)?

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return min(max(Double(done) / Double(total), 0), 1)
    }

    var body: some View {
        band
            .contentShape(Rectangle())
            .onTapGesture { onTap?() }
            .allowsHitTesting(onTap != nil)
    }

    private var band: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                stops: [
                    .init(color: palette.headerTop, location: 0),
                    .init(color: palette.headerMid, location: 0.52),
                    .init(color: palette.headerBottom, location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )

            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 0) {
                    Text("IDAG I FAMILJEN")
                        .font(.system(size: 10.5, weight: .bold))
                        .tracking(1.3)
                        .foregroundStyle(Color.white.opacity(0.84))
                    Spacer(minLength: 8)
                    if onTap != nil {
                        Text("Alla uppgifter")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.white)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                }

                Spacer().frame(height: 2)

                HStack(alignment: .lastTextBaseline, spacing: 7) {
                    Text("\(done)")
                        .font(.system(size: 40, weight: .bold))
                        .foregroundStyle(.white)
                    Text(total > 0 ? "av \(total) uppgifter" : "inga uppgifter planerade")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(Color.white.opacity(0.9))
                }

                Spacer().frame(height: 9)

                progressBar
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 20)
        }
        .frame(height: height)
        .frame(maxWidth: .infinity)
    }

    private var progressBar: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.34))
                if fraction > 0 {
                    Capsule()
                        .fill(Color.white)
                        .frame(width: geometry.size.width * fraction)
                }
            }
        }
        .frame(height: 5)
    }
}

// MARK: - Top bar

/// The family's name and the overflow menu, laid over the band.
///
/// It starts transparent with white text on the gradient and fades into a solid bar as
/// the band scrolls away. Pinned rather than scrolling, because this menu is the only
/// route to Prenumeration, Logga ut and deleting the family.
private struct DashboardTopBar: View {
    @Environment(\.seasonPalette) private var palette

    let familyName: String
    let collapsed: Double
    let height: CGFloat
    let onOpenSubscription: (() -> Void)?
    let onLogout: () -> Void
    let onDeleteFamily: (() -> Void)?
    let onOpenDeleteFamily: () -> Void
    /// Menypunkten finns bara när en kod är satt; se kommentaren vid den.
    var hasParentPin: Bool = false
    var onChangePin: () -> Void = {}

    var body: some View {
        HStack(spacing: 0) {
            Text(familyName)
                .font(.system(size: 20, weight: .bold))
                // White on the gradient, the season's ink once the bar is solid.
                .foregroundStyle(Color.white.mix(with: palette.ink, by: collapsed))
                .lineLimit(1)
                .truncationMode(.tail)

            Spacer(minLength: 8)

            Menu {
                // Android carries a "Mörkt läge" switch here. iOS has no equivalent
                // store or theme plumbing yet, so it is left out rather than shipped
                // as a switch that forgets what it was told.
                if let onOpenSubscription {
                    Button("Prenumeration", action: onOpenSubscription)
                }
                // Bara när en kod finns. Att SÄTTA den hör hemma i barnvyns banderoll,
                // där behovet uppstår -- ingen öppnar en meny för att leta efter ett lås
                // de inte vet finns. Att ÄNDRA den hör hemma här: den som ändrar vet
                // redan att koden existerar.
                if hasParentPin {
                    Button("Barnlåsets kod", action: onChangePin)
                }
                Button("Logga ut", action: onLogout)
                if onDeleteFamily != nil {
                    // Both stores require this to be reachable in the app, and Apple
                    // enforces it. Last in the menu and destructive, because it is the
                    // one entry here that cannot be undone. Öppnar ett blad som kräver
                    // att man skriver ordet, inte en knapp som bara raderar.
                    Section {
                        Button("Ta bort familjen", role: .destructive) {
                            onOpenDeleteFamily()
                        }
                    }
                }
            } label: {
                // Horizontal, not Android's vertical MoreVert: the ellipsis runs with
                // the text baseline on iOS.
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.white.mix(with: palette.inkSoft, by: collapsed))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Fler val")
        }
        .padding(.leading, 16)
        .padding(.trailing, 4)
        .frame(height: height)

        .background(
            // Through the safe area, not merely up to it. A bar that stops below the
            // status bar leaves the clock and the Dynamic Island printing onto
            // whatever the list has scrolled underneath them.
            palette.pageBg.opacity(collapsed)
                .ignoresSafeArea(edges: .top)
        )
    }
}

// MARK: - Child card

/// One child, as the thing the screen is actually about.
///
/// The pet is here because it is the app's whole proposition, and it used to appear
/// nowhere in the parent's view: a parent saw a name, three lines of grey text and
/// four buttons -- a database row with actions. The species carries its own colour
/// along the card's top edge, so each card matches the screen that child sees.
///
/// "Bjud in till appen" is not a permanent button. It is done once per child, so it
/// lives in the overflow menu and appears as a row here only while the phone is
/// unpaired.
private struct ChildCard: View {
    @Environment(\.seasonPalette) private var palette

    let child: AdultDashboardRepository.Child
    let onTasks: () -> Void
    let onPet: () -> Void
    let onWallet: () -> Void
    let onInvite: () -> Void
    let onChildView: (() -> Void)?
    let onManage: () -> Void

    private var species: PetThemeIOS.Palette { PetThemeIOS.forPet(child.petType) }

    private var allDoneToday: Bool {
        child.todaysTotal > 0 && child.todaysDone >= child.todaysTotal
    }

    private var subtitle: String {
        if child.loadFailed { return "Kunde inte läsa dagens sysslor" }
        guard let petType = child.petType, PetImagesIOS.petType(forEgg: petType) != nil else {
            return "Inget djur valt ännu"
        }
        return "\(PetNameUtilsIOS.getPetNameSwedish(petType)) · nivå \(child.growthStage)"
    }

    var body: some View {
        VStack(spacing: 0) {
            // A sliver of the child's own screen. The same gradient fills that screen
            // behind the pet, which is what ties the two together.
            PetThemeIOS.edge(child.petType)
                .frame(height: 3)

            VStack(alignment: .leading, spacing: 0) {
                header
                Spacer().frame(height: 14)
                if !child.hasPairedDevice {
                    unpairedRow
                    Spacer().frame(height: 8)
                }
                primaryButton
                Spacer().frame(height: 8)
                secondaryButtons
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity)
        .background(palette.surface.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: Color.black.opacity(palette.dark ? 0.3 : 0.08), radius: 4, y: 2)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 0) {
            ChildPetPortrait(
                petType: child.petType,
                growthStage: child.growthStage,
                done: child.todaysDone,
                total: child.todaysTotal,
                accent: species.accent,
                childName: child.name
            )

            Spacer().frame(width: 14)

            VStack(alignment: .leading, spacing: 0) {
                Text(child.name)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(palette.ink)

                Spacer().frame(height: 3)

                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(palette.inkSoft)

                // The chip under the name always answers the one question this screen
                // exists for: is this child done today. It used to be whatever fact
                // happened to be available, so a child with two chores left wore a
                // success-green badge about pocket money while the child who WAS done
                // wore the same green badge -- the same colour making opposite claims,
                // and the wrong one on the only card where it mattered.
                if !child.loadFailed {
                    Spacer().frame(height: 6)
                    if allDoneToday {
                        chip(icon: "checkmark", text: "Allt klart idag", tone: .done)
                    } else if child.todaysTotal == 0 {
                        Text("Inga sysslor planerade idag")
                            .font(.caption)
                            .foregroundStyle(palette.inkSoft)
                    } else {
                        let left = child.todaysTotal - child.todaysDone
                        chip(icon: "circle", text: "\(left) kvar idag", tone: .outstanding)
                    }
                }

                // Setting it up belongs in the wallet. Seeing that it is on belongs
                // where a parent already looks every day, so nobody has to remember
                // what they chose back in the summer -- but quietly, because it is a
                // standing arrangement rather than anything about today.
                if let note = child.allowanceNote {
                    Spacer().frame(height: 6)
                    chip(icon: "calendar", text: note, tone: .quiet)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Kept out of the name row so it keeps a full 44pt touch target without
            // stretching the text beside it.
            Menu {
                if let onChildView {
                    Button("Visa som barn", action: onChildView)
                }
                Button("Bjud in till appen", action: onInvite)
                Button("Inställningar", action: onManage)
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(palette.inkSoft)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Fler val för \(child.name)")
            .offset(x: 8, y: -10)
        }
    }

    /// Green is reserved for done. Anything else on this card that used it was
    /// borrowing a claim it had no right to make.
    private enum ChipTone { case done, outstanding, quiet }

    private func chip(icon: String, text: String, tone: ChipTone) -> some View {
        let ink: Color
        let fill: Color
        switch tone {
        case .done:
            ink = palette.goodInk
            fill = palette.goodBg
        case .outstanding:
            ink = palette.warnStrong
            fill = palette.warnBg
        case .quiet:
            ink = palette.inkSoft
            fill = palette.tipBg
        }
        return HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .semibold))
            Text(text)
                .font(.caption.weight(.semibold))
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(
            RoundedRectangle(cornerRadius: 6, style: .continuous).fill(fill)
        )
    }

    /// Only while it is outstanding. Once the phone is paired this row is gone for
    /// good, rather than becoming a button nobody will press again.
    private var unpairedRow: some View {
        Button(action: onInvite) {
            HStack(spacing: 8) {
                Text("Ingen telefon kopplad ännu")
                    .font(.footnote)
                    .foregroundStyle(palette.warnInk)
                Spacer(minLength: 8)
                Text("Bjud in")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.warnStrong)
            }
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(palette.warnBg)
            )
        }
        .buttonStyle(.plain)
    }

    private var primaryButton: some View {
        Button(action: onTasks) {
            HStack(spacing: 9) {
                Image(systemName: "checklist")
                    .font(.system(size: 17, weight: .semibold))
                Text("\(possessiveSwedish(child.name)) sysslor")
                    .font(.body.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .foregroundStyle(palette.onAccent)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.accent)
            )
        }
        .buttonStyle(.plain)
    }

    /// Quiet on purpose. Both are worth reaching, neither is a daily errand.
    private var secondaryButtons: some View {
        HStack(spacing: 8) {
            secondaryButton(
                icon: "pawprint.fill",
                iconTint: species.accent,
                title: "Djur",
                action: onPet
            )
            secondaryButton(
                icon: "wallet.bifold.fill",
                iconTint: palette.inkFaint,
                title: "Plånbok",
                action: onWallet
            )
        }
    }

    private func secondaryButton(
        icon: String,
        iconTint: Color,
        title: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: icon)
                    .font(.system(size: 15))
                    .foregroundStyle(iconTint)
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.outlineInk)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(palette.outlineBg)
                    .overlay(
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .strokeBorder(palette.outlineEdge, lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Pet portrait

/// A child's pet, ringed by how much of today's list is done.
///
/// The ring replaces the sentence "Idag: 3 av 5 uppgifter gjorda". A parent with three
/// children reads three rings at a glance; three sentences have to be read one at a
/// time. The exact figure stays in the badge for whoever wants it.
///
/// The portrait draws through PetVisual, so it is the same art over the same seasonal
/// background the child sees on their own screen and the two cannot drift apart.
private struct ChildPetPortrait: View {
    @Environment(\.seasonPalette) private var palette

    let petType: String?
    let growthStage: Int
    let done: Int
    let total: Int
    let accent: Color
    let childName: String

    private let size: CGFloat = 84
    private let petSize: CGFloat = 72
    private let stroke: CGFloat = 4

    private var fraction: Double {
        guard total > 0 else { return 0 }
        return min(max(Double(done) / Double(total), 0), 1)
    }

    var body: some View {
        ZStack {
            // The track is always drawn, so a child with nothing done still reads as
            // "0 of 4" rather than as a card that failed to load. 0.15 vanished
            // against a deep accent like dragon's violet.
            Circle()
                .strokeBorder(accent.opacity(0.22), lineWidth: stroke)

            if fraction > 0 {
                Circle()
                    .inset(by: stroke / 2)
                    .trim(from: 0, to: fraction)
                    .stroke(accent, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                    // SwiftUI starts a trim at three o'clock; the ring has to start at
                    // the top, which is what Compose's startAngle = -90f does.
                    .rotationEffect(.degrees(-90))
            }

            pet
        }
        .frame(width: size, height: size)
        .overlay(alignment: .bottomTrailing) {
            if total > 0 { badge }
        }
    }

    @ViewBuilder
    private var pet: some View {
        if petType != nil {
            PetVisual(
                petType: petType,
                growthStage: growthStage,
                // Half the diameter, so the frame is a circle rather than a rounded square.
                cornerRadius: petSize / 2,
                alignment: .bottom
            )
            .frame(width: petSize, height: petSize)
            .accessibilityLabel("\(possessiveSwedish(childName)) djur")
        } else {
            Circle()
                .fill(accent.opacity(0.10))
                .frame(width: petSize, height: petSize)
                .overlay(
                    Image(systemName: "pawprint.fill")
                        .font(.system(size: 24))
                        .foregroundStyle(accent.opacity(0.5))
                )
        }
    }

    private var badge: some View {
        Text("\(done)/\(total)")
            .font(.system(size: 10.5, weight: .bold))
            .foregroundStyle(palette.ink)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(palette.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .strokeBorder(accent, lineWidth: 1.5)
                    )
            )
    }
}

// MARK: - Adult row

private struct AdultRow: View {
    @Environment(\.seasonPalette) private var palette

    let adult: AdultDashboardRepository.Adult
    let onInvite: () -> Void
    let onManage: () -> Void

    private var initial: String {
        String(adult.name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1)).uppercased()
    }

    private var roleLine: String {
        let role = adult.role == "ASSISTANT" ? "Vuxen" : "Förälder"
        return adult.hasPairedDevice ? role : "\(role) · ingen telefon kopplad"
    }

    var body: some View {
        HStack(spacing: 0) {
            Circle()
                .fill(palette.calBg)
                .frame(width: 40, height: 40)
                .overlay(
                    Text(initial)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.calInk)
                )

            Spacer().frame(width: 12)

            VStack(alignment: .leading, spacing: 0) {
                Text(adult.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(palette.ink)
                Text(roleLine)
                    .font(.footnote)
                    .foregroundStyle(palette.inkSoft)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // In the SAME slot the menu occupies for everyone else, not before it.
            // Sitting ahead of a reserved-but-invisible menu put it at a different
            // distance from the edge than the other rows' menus, so the column's
            // trailing edge stepped in and out down the list.
            //
            // Replaces "(du)" appended to the name, which grew the one string a parent
            // scans for.
            if adult.isCurrentUser {
                Text("Du")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(palette.onAccent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous).fill(palette.accent)
                    )
            }

            Menu {
                if !adult.isCurrentUser {
                    Button("Koppla telefon", action: onInvite)
                }
                Button("Inställningar", action: onManage)
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(palette.inkSoft)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Fler val för \(adult.name)")
            // Alla får en meny numera. Den inloggade föräldern har något att välja
            // här: byta sitt eget namn och sätta sitt eget lösenord. Det som inte
            // gäller dem -- koppla telefon, ta bort sig själv -- utelämnas inne i
            // menyn i stället för att menyn försvinner.
        }
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .padding(.vertical, 8)
        .background(palette.surface.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: Color.black.opacity(palette.dark ? 0.24 : 0.05), radius: 2, y: 1)
    }
}

// MARK: - Swedish helpers

/// Swedish possessive.
///
/// A name already ending in s, x or z takes no extra s, so a plain `"\(name)s"`
/// produced "Nilss sysslor" for a perfectly ordinary Swedish name.
/// Delad med kodrutan, som säger vems vy man lämnar.
func possessiveSwedish(_ name: String) -> String {
    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
    guard let last = trimmed.lowercased().last else { return trimmed }
    return ["s", "x", "z"].contains(String(last)) ? trimmed : "\(trimmed)s"
}

// MARK: - Fixture

#if DEBUG
extension AdultDashboardView {

    /// The screen with sample data and no session, so it can be photographed.
    ///
    /// The iOS simulator hands over a screenshot but takes no input, so a screen
    /// behind a login cannot be reached to be looked at at all. This is the way in --
    /// see ScreenHarness in KidQuestApp.swift.
    ///
    /// The two children are deliberately different shapes: one part-way through the
    /// day with a pet and a standing allowance, one finished but with no phone paired,
    /// which is the pair of states the card has to hold at once.
    /// En helt ny familj: inga barn, inga sysslor, inget djur. Det är enda läget där
    /// kom igång-guiden visas hel, och därmed enda sättet att titta på den -- guiden
    /// viker ihop sig så snart något steg är klart.
    static func fixtureNewFamily() -> AdultDashboardView {
        AdultDashboardView(
            onFamilyTasks: {},
            onOpenSubscription: {},
            onDeleteFamily: {},
            onChildView: { _, _ in },
            preloaded: AdultDashboardRepository.Overview(
                familyName: "Melander",
                children: [],
                adults: []
            )
        )
    }

    static func fixture(pets: Bool = true) -> AdultDashboardView {
        AdultDashboardView(
            // Wired to no-ops rather than left nil: every affordance the Android
            // screenshots show should be visible in the photograph, including the
            // "Alla uppgifter" chevron and the full overflow menu.
            onFamilyTasks: {},
            onOpenSubscription: {},
            onDeleteFamily: {},
            onChildView: { _, _ in },
            preloaded: AdultDashboardRepository.Overview(
                familyName: "Melander",
                children: [
                    AdultDashboardRepository.Child(
                        id: "child-1",
                        name: "Signe",
                        hasPairedDevice: true,
                        todaysDone: 3,
                        todaysTotal: 5,
                        petType: pets ? "dragon" : nil,
                        growthStage: 3,
                        allowanceNote: "50 kr varje fredag",
                        loadFailed: false
                    ),
                    AdultDashboardRepository.Child(
                        id: "child-2",
                        name: "Walter",
                        hasPairedDevice: false,
                        todaysDone: 4,
                        todaysTotal: 4,
                        petType: pets ? "cat" : nil,
                        growthStage: 2,
                        allowanceNote: nil,
                        loadFailed: false
                    ),
                ],
                adults: [
                    AdultDashboardRepository.Adult(
                        id: "adult-1",
                        name: "Patrik",
                        role: "PARENT",
                        hasPairedDevice: true,
                        isCurrentUser: true
                    ),
                    AdultDashboardRepository.Adult(
                        id: "adult-2",
                        name: "Jessica",
                        role: "PARENT",
                        hasPairedDevice: false,
                        isCurrentUser: false
                    ),
                ]
            )
        )
    }
}

#Preview("Föräldravy") {
    AdultDashboardView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: false))
}

#Preview("Föräldravy mörk") {
    AdultDashboardView.fixture()
        .environment(\.seasonPalette, SeasonTheme.current(dark: true))
        .preferredColorScheme(.dark)
}
#endif
