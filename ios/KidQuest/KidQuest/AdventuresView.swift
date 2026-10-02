import SwiftUI
import Combine

/// Äventyrsskärmen: biljettsaldo, val av scen, server-styrd nedräkning, en kista som
/// öppnas vid claim, och ram-inventariet. `memberId` nil = egen token; satt = en förälder
/// som agerar i barnets vy (member-scoped endpoints), precis som SelectEggSheet.
struct AdventuresView: View {
    let childName: String
    var memberId: String?
    var onBack: () -> Void = {}
    /// Leads to the child's chores from the no-ticket state -- that is where tickets are earned.
    /// Nil hides the button.
    var onOpenTasks: (() -> Void)?

    /// Non-nil renders this instead of calling the network. Only `fixture()` sets it; a
    /// plain stored property so the memberwise initialiser is the same in every build.
    var preloaded: Preloaded?

    struct Preloaded {
        let state: AdventureStateDTO
        let inventory: [InventoryItemDTO]
        let catalog: [LootCatalogItemDTO]
        let equippedFrame: String?
        let equippedSceneItem: String?
        var pet: PetResponseDTO? = nil
        var xp: XpProgressResponseDTO? = nil
        var eggs: [EggCollectionItemDTO] = []
    }

    @State private var state: AdventureStateDTO?
    @State private var inventory: [InventoryItemDTO] = []
    @State private var catalog: [String: LootCatalogItemDTO] = [:]
    @State private var equippedFrame: String?
    @State private var equippedSceneItem: String?
    @State private var loading = true
    @State private var busy = false
    @State private var error: String?
    @State private var loot: ClaimLootResponseDTO?
    @State private var loadedAt = Date()
    @State private var now = Date()
    @State private var pet: PetResponseDTO?
    @State private var xp: XpProgressResponseDTO?
    /// Rare and legendary eggs not yet unlocked; nil when the eggs could not be read.
    @State private var rareEggsLeft: Int?

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    private struct AdventureScene: Identifiable {
        let key: String
        let label: String
        var id: String { key }
    }
    private let scenes: [AdventureScene] = [
        .init(key: "glade", label: String(localized: "Gläntan")),
        .init(key: "forest", label: String(localized: "Skogen")),
        .init(key: "snow", label: String(localized: "Snöstigen")),
        .init(key: "mountain", label: String(localized: "Berget")),
        .init(key: "cave", label: String(localized: "Grottan")),
        .init(key: "reef", label: String(localized: "Korallrevet")),
    ]
    private let cols = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8),
                        GridItem(.flexible(), spacing: 8)]

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(title: String(localized: "Äventyr"), subtitle: childName, onBack: onBack)
            if loading {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                content
            }
        }
        .background(palette.pageBg.ignoresSafeArea())
        .task { await load() }
        .onReceive(tick) { now = $0 }
        .overlay {
            if let l = loot {
                LootReveal(loot: l, palette: palette) { loot = nil }
            }
        }
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if let error { Text(error).foregroundStyle(.red) }

                ticketBadge

                let ongoing = state?.adventures.filter { $0.status == "ONGOING" } ?? []

                if (state?.ticketBalance ?? 0) > 0 {
                    sectionTitle(String(localized: "Skicka på äventyr"))
                    sceneGrid
                } else if ongoing.isEmpty {
                    noTicketHero
                    lockedScenes
                    curiosityLine
                }

                if !ongoing.isEmpty {
                    sectionTitle(String(localized: "På äventyr")).padding(.top, 4)
                    ForEach(ongoing) { adv in
                        ongoingCard(adv)
                    }
                }

                framesSection
                decorationsSection
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    // MARK: - No tickets

    /// Rare and legendary eggs this child has not unlocked yet.
    private static func rareLeft(_ eggs: [EggCollectionItemDTO]) -> Int {
        eggs.filter { $0.rarity != "COMMON" && !$0.unlocked }.count
    }

    /// XP left to the next ticket, and how far along the way the child is (0...1).
    /// Tickets come at every level-up, and after level 5 at every star (every 50 XP).
    private var nextTicket: (remaining: Int, progress: Double)? {
        guard let xp else { return nil }
        if xp.currentLevel < 5 {
            let span = xp.xpInCurrentLevel + xp.xpForNextLevel
            guard span > 0 else { return nil }
            return (xp.xpForNextLevel, Double(xp.xpInCurrentLevel) / Double(span))
        }
        let perStar = 50
        let remaining = min(max(xp.xpToNextStar ?? perStar, 0), perStar)
        return (remaining, Double(perStar - remaining) / Double(perStar))
    }

    private var heroTitle: String {
        if let name = pet?.name?.trimmingCharacters(in: .whitespaces), !name.isEmpty {
            return String(localized: "\(name) har packat ryggsäcken! 🎒")
        }
        return String(localized: "Ryggsäcken är packad! 🎒")
    }

    /// Instead of a grey "no tickets" line: the child's own pet, ready to go, and exactly
    /// how close the next ticket is. A wait reads as a goal, not a wall.
    private var noTicketHero: some View {
        VStack(spacing: 14) {
            HStack(alignment: .bottom, spacing: -18) {
                if let name = PetImagesIOS.petImageName(for: pet?.petType, growthStage: pet?.growthStage ?? 1),
                   let img = UIImage(named: name) {
                    Image(uiImage: img).resizable().scaledToFit()
                        .frame(width: 120, height: 120)
                        .zIndex(1)
                }
                Image("adventure_backpack").resizable().scaledToFit()
                    .frame(width: 130, height: 130)
            }
            .frame(maxWidth: .infinity)
            .accessibilityHidden(true)

            Text(heroTitle)
                .font(.title3.weight(.bold))
                .multilineTextAlignment(.center)
                .foregroundStyle(palette.ink)

            if let next = nextTicket {
                VStack(spacing: 8) {
                    Text(next.remaining == 1
                         ? String(localized: "Bara 1 XP kvar till nästa biljett")
                         : String(localized: "Bara \(next.remaining) XP kvar till nästa biljett"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.tipStrong)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(palette.track)
                            Capsule()
                                .fill(LinearGradient(
                                    colors: [Color(red: 0.92, green: 0.70, blue: 0.03),
                                             Color(red: 0.99, green: 0.88, blue: 0.28)],
                                    startPoint: .leading, endPoint: .trailing))
                                .frame(width: max(12, geo.size.width * next.progress))
                        }
                    }
                    .frame(height: 12)
                    .accessibilityElement()
                    .accessibilityLabel(String(localized: "Mot nästa biljett"))
                    .accessibilityValue("\(Int((next.progress * 100).rounded())) %")
                }
            } else {
                Text("Klara fler nivåer för att få en äventyrsbiljett.")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(palette.inkSoft)
            }

            if let onOpenTasks {
                Button(action: onOpenTasks) {
                    Text("Gör dagens sysslor")
                        .font(.body.weight(.bold))
                        .foregroundStyle(palette.onAccent)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(palette.accent))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(palette.tipBg))
    }

    /// The places waiting behind the next ticket, shown but locked -- something to want.
    private var lockedScenes: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(String(localized: "Här väntar nästa äventyr")).padding(.top, 4)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(scenes) { scene in
                    ZStack(alignment: .bottomLeading) {
                        if let name = PetImagesIOS.sceneImageName(scene.key), let img = UIImage(named: name) {
                            Image(uiImage: img).resizable().scaledToFill()
                                .frame(height: 92).frame(maxWidth: .infinity)
                                .clipped()
                                .grayscale(0.55)
                                .opacity(0.8)
                        } else {
                            Rectangle().fill(palette.cardEdge).frame(height: 92)
                        }
                        LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom)
                        Text(scene.label)
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(10)
                    }
                    .frame(height: 92)
                    .frame(maxWidth: .infinity)
                    .overlay(alignment: .topTrailing) {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(palette.ink)
                            .padding(7)
                            .background(Circle().fill(.white.opacity(0.9)))
                            .padding(8)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(String(localized: "\(scene.label), låst"))
                }
            }
        }
    }

    @ViewBuilder
    private var curiosityLine: some View {
        if let left = rareEggsLeft, left > 0 {
            HStack(spacing: 10) {
                HStack(spacing: 4) {
                    ForEach(0..<3, id: \.self) { _ in
                        Ellipse()
                            .fill(palette.cardEdge)
                            .frame(width: 22, height: 28)
                            .overlay(Text("?").font(.caption.weight(.heavy)).foregroundStyle(palette.inkFaint))
                    }
                }
                .accessibilityHidden(true)
                Text(left == 1
                     ? String(localized: "1 sällsynt ägg kvar att upptäcka")
                     : String(localized: "\(left) sällsynta ägg kvar att upptäcka"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.inkSoft)
            }
            .padding(.top, 2)
        }
    }

    private func sectionTitle(_ t: String) -> some View {
        Text(t).font(.headline).foregroundStyle(palette.ink)
    }

    private var ticketBadge: some View {
        let balance = state?.ticketBalance ?? 0
        return HStack(spacing: 6) {
            Text("🎟️").font(.title3)
            Text(balance == 1 ? "1 biljett" : "\(balance) biljetter")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(palette.tipStrong)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Capsule().fill(palette.tipBg))
    }

    /// Stora, inbjudande scenkort i en kolumn. Namnet ligger över bilden och ett
    /// "Skicka"-märke gör tryckhandlingen tydlig. Ett tryck skickar iväg djuret direkt.
    private var sceneGrid: some View {
        VStack(spacing: 12) {
            ForEach(scenes) { scene in
                Button {
                    Task { await startAdventure(scene.key) }
                } label: {
                    ZStack(alignment: .bottom) {
                        if let name = PetImagesIOS.sceneImageName(scene.key), let img = UIImage(named: name) {
                            Image(uiImage: img).resizable().scaledToFill()
                                .frame(height: 150).frame(maxWidth: .infinity)
                                .clipped()
                        } else {
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(palette.cardEdge).frame(height: 150)
                        }
                        LinearGradient(
                            colors: [.clear, .black.opacity(0.6)],
                            startPoint: .center, endPoint: .bottom
                        )
                        HStack(alignment: .bottom) {
                            Text(scene.label)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(.white)
                            Spacer()
                            HStack(spacing: 4) {
                                Text("🗺️").font(.footnote)
                                Text("Skicka").font(.subheadline.weight(.bold))
                                    .foregroundStyle(.black.opacity(0.8))
                            }
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(Capsule().fill(.white.opacity(0.9)))
                        }
                        .padding(14)
                    }
                    .frame(height: 150)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(busy)
            }
        }
    }

    private func ongoingCard(_ adv: AdventureResponseDTO) -> some View {
        let remaining = remainingSecs(adv)
        let ready = remaining <= 0
        let scene = scenes.first { $0.key == adv.scene }
        return HStack(spacing: 12) {
            if let key = scene?.key, let name = PetImagesIOS.sceneImageName(key), let img = UIImage(named: name) {
                Image(uiImage: img).resizable().scaledToFill().frame(width: 48, height: 48)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            } else {
                Text("🗺️").font(.title2)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(scene?.label ?? String(localized: "Äventyr")).font(.subheadline.weight(.bold)).foregroundStyle(palette.ink)
                Text(ready ? String(localized: "Djuret är hemma!") : String(localized: "Hemma om \(formatRemaining(remaining))"))
                    .font(.body)
                    .foregroundStyle(ready ? palette.goodInk : palette.inkFaint)
            }
            Spacer()
            if ready {
                Button("Hämta") { Task { await claim(adv.id) } }
                    .font(.body.weight(.bold))
                    .foregroundStyle(palette.goodInk)
                    .disabled(busy)
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(ready ? palette.goodBg : palette.surface))
        .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous)
            .stroke(ready ? palette.goodInk.opacity(0.4) : palette.cardEdge, lineWidth: 1.5))
    }

    @ViewBuilder
    private var framesSection: some View {
        // Split owned cosmetics by catalog type; items missing from the catalog fall back
        // to frames, matching the pre-scene-item behaviour.
        let frames = inventory.map { $0.itemId }.filter { catalog[$0]?.type != "SCENE_ITEM" }
        if !frames.isEmpty {
            sectionTitle(String(localized: "Dina ramar")).padding(.top, 4)
            let options: [String?] = [nil] + frames
            LazyVGrid(columns: cols, spacing: 8) {
                ForEach(Array(options.enumerated()), id: \.offset) { _, frameId in
                    frameTile(frameId)
                }
            }
        }
    }

    @ViewBuilder
    private var decorationsSection: some View {
        let items = inventory.map { $0.itemId }.filter { catalog[$0]?.type == "SCENE_ITEM" }
        if !items.isEmpty {
            sectionTitle(String(localized: "Dina dekorationer")).padding(.top, 4)
            let options: [String?] = [nil] + items
            LazyVGrid(columns: cols, spacing: 8) {
                ForEach(Array(options.enumerated()), id: \.offset) { _, itemId in
                    decorationTile(itemId)
                }
            }
        }
    }

    private func decorationTile(_ itemId: String?) -> some View {
        let selected = itemId == equippedSceneItem
        let label = itemId.flatMap { catalog[$0]?.name }
        return Button {
            Task { await equipSceneItem(itemId) }
        } label: {
            VStack(spacing: 4) {
                if itemId == nil {
                    Text("Ingen")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(palette.inkFaint)
                        .frame(height: 52)
                } else if let name = PetImagesIOS.sceneItemImageName(itemId), let img = UIImage(named: name) {
                    Image(uiImage: img).resizable().scaledToFit().frame(height: 52)
                    Text(label ?? String(localized: "Dekoration"))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(palette.ink)
                        .lineLimit(1)
                } else {
                    Text("✨").font(.title2).frame(height: 52)
                    Text(label ?? String(localized: "Dekoration"))
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(palette.ink)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 84)
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 15, style: .continuous)
                .fill(selected ? palette.tipBg : palette.surface))
            .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(selected ? palette.accent : palette.cardEdge, lineWidth: selected ? 2.5 : 1.5))
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }

    private func frameTile(_ frameId: String?) -> some View {
        let selected = frameId == equippedFrame
        return Button {
            Task { await equipFrame(frameId) }
        } label: {
            ZStack {
                if frameId == nil {
                    Text("Ingen ram")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(palette.inkFaint)
                        .multilineTextAlignment(.center)
                } else if let name = PetImagesIOS.frameImageName(frameId), let img = UIImage(named: name) {
                    Image(uiImage: img).resizable().scaledToFit().frame(width: 64, height: 64)
                } else {
                    Text("🖼️").font(.title2)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 84)
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 15, style: .continuous)
                .fill(selected ? palette.tipBg : palette.surface))
            .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(selected ? palette.accent : palette.cardEdge, lineWidth: selected ? 2.5 : 1.5))
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }

    // MARK: - Logic

    private func remainingSecs(_ adv: AdventureResponseDTO) -> Int {
        let elapsed = Int(now.timeIntervalSince(loadedAt))
        return max(0, adv.secondsRemaining - elapsed)
    }

    private func load() async {
        if let preloaded {
            state = preloaded.state
            inventory = preloaded.inventory
            catalog = Dictionary(uniqueKeysWithValues: preloaded.catalog.map { ($0.id, $0) })
            equippedFrame = preloaded.equippedFrame
            equippedSceneItem = preloaded.equippedSceneItem
            pet = preloaded.pet
            xp = preloaded.xp
            rareEggsLeft = preloaded.eggs.isEmpty ? nil : Self.rareLeft(preloaded.eggs)
            loadedAt = Date()
            loading = false
            return
        }
        loading = true
        error = nil
        do {
            let s = try await AdventureRepository.state(memberId: memberId)
            let inv = try await AdventureRepository.inventory(memberId: memberId)
            let cat = await AdventureRepository.lootCatalog()
            let pet = await AdventureRepository.currentPet(memberId: memberId)
            // Only needed for the no-ticket state, and never worth failing the screen over.
            async let xpLoad: XpProgressResponseDTO? = try? ApiClient.shared.send(
                XpProgressResponseDTO.self,
                path: memberId.map { "xp/members/\($0)/current" } ?? "xp/current",
                method: "GET")
            async let eggsLoad: [EggCollectionItemDTO]? = try? AdventureRepository.eggs(memberId: memberId)
            let xpResult = await xpLoad
            let eggsResult = await eggsLoad
            await MainActor.run {
                self.pet = pet
                xp = xpResult
                rareEggsLeft = eggsResult.map(Self.rareLeft)
                state = s
                inventory = inv
                catalog = Dictionary(uniqueKeysWithValues: cat.map { ($0.id, $0) })
                equippedFrame = pet?.equippedFrame
                equippedSceneItem = pet?.equippedSceneItem
                loadedAt = Date()
                loading = false
            }
        } catch {
            await MainActor.run {
                self.error = String(localized: "Kunde inte hämta äventyr.")
                loading = false
            }
        }
    }

    private func startAdventure(_ scene: String) async {
        if busy { return }
        busy = true
        error = nil
        do {
            _ = try await AdventureRepository.start(memberId: memberId, scene: scene)
            // Gå direkt tillbaka till bandet så barnet genast ser djuret på äventyr.
            await MainActor.run { onBack() }
        } catch {
            await MainActor.run { self.error = String(localized: "Kunde inte skicka iväg djuret.") }
        }
        await MainActor.run { busy = false }
    }

    private func claim(_ id: String) async {
        if busy { return }
        busy = true
        error = nil
        do {
            let won = try await AdventureRepository.claim(memberId: memberId, adventureId: id)
            await MainActor.run { loot = won }
            await load()
        } catch {
            await MainActor.run { self.error = String(localized: "Kunde inte hämta belöningen.") }
        }
        await MainActor.run { busy = false }
    }

    private func equipFrame(_ frameId: String?) async {
        if busy { return }
        busy = true
        error = nil
        do {
            let pet = try await AdventureRepository.setFrame(memberId: memberId, frameId: frameId)
            await MainActor.run { equippedFrame = pet.equippedFrame }
        } catch {
            await MainActor.run { self.error = String(localized: "Kunde inte byta ram.") }
        }
        await MainActor.run { busy = false }
    }

    private func equipSceneItem(_ itemId: String?) async {
        if busy { return }
        busy = true
        error = nil
        do {
            let pet = try await AdventureRepository.setSceneItem(memberId: memberId, itemId: itemId)
            await MainActor.run { equippedSceneItem = pet.equippedSceneItem }
        } catch {
            await MainActor.run { self.error = String(localized: "Kunde inte byta dekoration.") }
        }
        await MainActor.run { busy = false }
    }

    private func formatRemaining(_ secs: Int) -> String {
        let m = secs / 60
        let s = secs % 60
        return m >= 3 ? String(localized: "\(m) min") : String(format: "%d:%02d", m, s)
    }
}

/// Kistan öppnas i fem steg och sedan tonar belöningen fram — samma dramaturgi som
/// äggkläckningen. Går inte att stänga förrän kistan öppnats.
struct LootReveal: View {
    let loot: ClaimLootResponseDTO
    let palette: SeasonPalette
    let onDismiss: () -> Void

    @State private var stage = 1
    @State private var revealed = false

    private var reward: (emoji: String, message: String) {
        switch loot.type {
        case "EGG": return ("🥚", String(localized: "Du hittade ett nytt ägg! Det väntar i äggväljaren."))
        case "FRAME": return ("🖼️", String(localized: "En ny ram till din scen!"))
        case "SCENE_ITEM": return ("✨", String(localized: "En ny dekoration till din scen!"))
        default:
            return ("🍎", loot.quantity == 1 ? String(localized: "Du hittade 1 mat till ditt djur!")
                : String(localized: "Du hittade \(loot.quantity) mat till ditt djur!"))
        }
    }

    /// Den faktiska konsten för lootet, när den finns — ett ägg visar hur det ser ut, så
    /// barnet blir nyfiket inför nästa månadsskifte. Faller tillbaka på emojin annars.
    private var rewardImageName: String? {
        switch loot.type {
        case "EGG": return PetImagesIOS.eggImageName(for: loot.ref)
        case "FRAME": return PetImagesIOS.frameImageName(loot.ref)
        case "SCENE_ITEM": return PetImagesIOS.sceneItemImageName(loot.ref)
        default: return nil
        }
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea()
            VStack(spacing: 14) {
                Text(revealed ? "Titta vad du fick!" : "Öppnar kistan…")
                    .font(.headline)
                    .foregroundStyle(palette.ink)
                if let name = PetImagesIOS.chestStageImageName(stage), let img = UIImage(named: name) {
                    Image(uiImage: img).resizable().scaledToFit().frame(width: 160, height: 160)
                } else {
                    Text("🧰").font(.system(size: 72))
                }
                if revealed {
                    if let name = rewardImageName, let img = UIImage(named: name) {
                        Image(uiImage: img).resizable().scaledToFit().frame(width: 96, height: 96)
                    } else {
                        Text(reward.emoji).font(.system(size: 44))
                    }
                    Text(reward.message)
                        .font(.body)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(palette.ink)
                    Button("Toppen!") { onDismiss() }
                        .font(.body.weight(.bold))
                        .foregroundStyle(palette.accent)
                        .padding(.top, 4)
                }
            }
            .padding(24)
            .frame(maxWidth: 320)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.surface))
            .padding(32)
        }
        .task {
            for s in 1...5 {
                stage = s
                try? await Task.sleep(for: .seconds(0.32))
            }
            try? await Task.sleep(for: .seconds(0.2))
            revealed = true
        }
    }
}

#if DEBUG
extension AdventuresView {

    /// No tickets and nothing under way: the waiting state, with Kvitter seven XP from
    /// the next level and five rare eggs still to find. KQ_SCREEN=adventures-empty.
    static func emptyFixture() -> AdventuresView {
        let rare = ["orange_egg", "black_egg", "cyan_egg", "gray_egg", "silver_egg"].map {
            EggCollectionItemDTO(eggType: $0, petType: "bear", rarity: "RARE", unlocked: false, collected: false)
        }
        return AdventuresView(
            childName: "Ella",
            onOpenTasks: {},
            preloaded: Preloaded(
                state: AdventureStateDTO(ticketBalance: 0, adventures: []),
                inventory: [],
                catalog: [],
                equippedFrame: nil,
                equippedSceneItem: nil,
                pet: ChildFixtures.pet,
                xp: XpProgressResponseDTO(
                    id: "x1", memberId: "child-1", year: 2026, month: 10,
                    currentXp: 63, currentLevel: 3, totalTasksCompleted: 30,
                    xpForNextLevel: 7, xpInCurrentLevel: 28, stars: 0, xpToNextStar: 0
                ),
                eggs: rare
            )
        )
    }

    /// The adventures screen with sample data and no session, for store screenshots:
    /// tickets to spend, one adventure under way, and a few frames and decorations won.
    /// See ScreenHarness in KidQuestApp.swift.
    static func fixture() -> AdventuresView {
        let items = [
            ("frame_forest", "FRAME", "COMMON", String(localized: "Skogsram")),
            ("frame_crystal", "FRAME", "RARE", String(localized: "Kristallram")),
            ("frame_royal", "FRAME", "LEGENDARY", String(localized: "Kungaram")),
            ("item_kite", "SCENE_ITEM", "COMMON", String(localized: "Drake i himlen")),
            ("item_lanterns", "SCENE_ITEM", "RARE", String(localized: "Lyktor")),
            ("item_shootingstar", "SCENE_ITEM", "LEGENDARY", String(localized: "Stjärnfall")),
        ]
        return AdventuresView(
            childName: "Ella",
            preloaded: Preloaded(
                state: AdventureStateDTO(
                    ticketBalance: 3,
                    adventures: [
                        AdventureResponseDTO(
                            id: "a1", scene: "forest", status: "ONGOING",
                            durationSecs: 900, secondsRemaining: 412, ready: false,
                            lootType: nil, lootRef: nil, lootQty: nil,
                            startedAt: "2026-09-27T09:30:00Z"
                        ),
                    ]
                ),
                inventory: items.map { InventoryItemDTO(itemId: $0.0, acquiredAt: "2026-09-20T12:00:00Z") },
                catalog: items.map {
                    LootCatalogItemDTO(id: $0.0, type: $0.1, rarity: $0.2, name: $0.3, assetKey: $0.0, anchor: nil)
                },
                equippedFrame: "frame_crystal",
                equippedSceneItem: "item_lanterns"
            )
        )
    }
}
#endif
