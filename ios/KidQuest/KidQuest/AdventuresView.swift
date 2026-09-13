import SwiftUI
import Combine

/// Äventyrsskärmen: biljettsaldo, val av scen, server-styrd nedräkning, en kista som
/// öppnas vid claim, och ram-inventariet. `memberId` nil = egen token; satt = en förälder
/// som agerar i barnets vy (member-scoped endpoints), precis som SelectEggSheet.
struct AdventuresView: View {
    let childName: String
    var memberId: String?
    var onBack: () -> Void = {}

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

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    private struct AdventureScene: Identifiable {
        let key: String
        let label: String
        var id: String { key }
    }
    private let scenes: [AdventureScene] = [
        .init(key: "glade", label: "Gläntan"),
        .init(key: "forest", label: "Skogen"),
        .init(key: "snow", label: "Snöstigen"),
        .init(key: "mountain", label: "Berget"),
        .init(key: "cave", label: "Grottan"),
        .init(key: "reef", label: "Korallrevet"),
    ]
    private let cols = [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8),
                        GridItem(.flexible(), spacing: 8)]

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(title: "Äventyr", subtitle: childName, onBack: onBack)
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
                    sectionTitle("Skicka på äventyr")
                    sceneGrid
                } else if ongoing.isEmpty {
                    Text("Klara fler nivåer för att få en äventyrsbiljett.")
                        .font(.body)
                        .foregroundStyle(palette.inkFaint)
                }

                if !ongoing.isEmpty {
                    sectionTitle("På äventyr").padding(.top, 4)
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
                Text(scene?.label ?? "Äventyr").font(.subheadline.weight(.bold)).foregroundStyle(palette.ink)
                Text(ready ? "Djuret är hemma!" : "Hemma om \(formatRemaining(remaining))")
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
            sectionTitle("Dina ramar").padding(.top, 4)
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
            sectionTitle("Dina dekorationer").padding(.top, 4)
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
                    Text(label ?? "Dekoration")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(palette.ink)
                        .lineLimit(1)
                } else {
                    Text("✨").font(.title2).frame(height: 52)
                    Text(label ?? "Dekoration")
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
        loading = true
        error = nil
        do {
            let s = try await AdventureRepository.state(memberId: memberId)
            let inv = try await AdventureRepository.inventory(memberId: memberId)
            let cat = await AdventureRepository.lootCatalog()
            let pet = await AdventureRepository.currentPet(memberId: memberId)
            await MainActor.run {
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
                self.error = "Kunde inte hämta äventyr."
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
            await MainActor.run { self.error = "Kunde inte skicka iväg djuret." }
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
            await MainActor.run { self.error = "Kunde inte hämta belöningen." }
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
            await MainActor.run { self.error = "Kunde inte byta ram." }
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
            await MainActor.run { self.error = "Kunde inte byta dekoration." }
        }
        await MainActor.run { busy = false }
    }

    private func formatRemaining(_ secs: Int) -> String {
        let m = secs / 60
        let s = secs % 60
        return m >= 3 ? "\(m) min" : String(format: "%d:%02d", m, s)
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
        case "EGG": return ("🥚", "Du hittade ett nytt ägg! Det väntar i äggväljaren.")
        case "FRAME": return ("🖼️", "En ny ram till din scen!")
        case "SCENE_ITEM": return ("✨", "En ny dekoration till din scen!")
        default:
            return ("🍎", loot.quantity == 1 ? "Du hittade 1 mat till ditt djur!"
                : "Du hittade \(loot.quantity) mat till ditt djur!")
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
