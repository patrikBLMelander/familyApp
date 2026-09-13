import SwiftUI

/// Äggväljaren som en samlingstavla, i tre zoner.
///
/// Valbara ägg först (upplåsta och inte samlade), sedan oupptäckta som mystery-ägg — utan
/// att avslöja djur eller sällsynthet, för gåtan är poängen och vägen dit är äventyr — och
/// sist de samlade djuren ur historiken, med månaden de kom. Android har samma tavla i
/// EggCollectionBoard.kt.
struct EggCollectionBoard: View {
    let eggs: [EggCollectionItemDTO]
    let history: [PetHistoryResponseDTO]
    let selectedEgg: String?
    let palette: SeasonPalette
    let onSelect: (String) -> Void

    private let columns = [
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
    ]

    private var selectable: [EggCollectionItemDTO] { eggs.filter { $0.unlocked && !$0.collected } }
    private var mystery: [EggCollectionItemDTO] { eggs.filter { !$0.unlocked } }
    private var taken: [PetHistoryResponseDTO] {
        history.sorted { a, b in
            a.year != b.year ? a.year > b.year : a.month > b.month
        }
    }

    var body: some View {
        VStack(spacing: 8) {
            tiersSection
            if !mystery.isEmpty {
                Text("Skicka djuret på äventyr för att hitta fler.")
                    .font(.system(size: 11))
                    .foregroundStyle(palette.inkFaint)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 2)
            }
            collectedSection
        }
    }

    /// Hela väljaren grupperad per sällsynthetstier. Ordning och rubriker på svenska.
    private static let rarityTiers: [(key: String, label: String)] = [
        ("COMMON", "Vanliga"),
        ("RARE", "Sällsynta"),
        ("LEGENDARY", "Legendariska"),
        ("MYTHIC", "Mytiska"),
    ]

    /// Varje tier visar sina ägg som ännu inte samlats: de valbara (upplåsta, tryckbara)
    /// först och de oupptäckta ("?") efter -- så en vunnen sällsynthet hamnar under
    /// "Sällsynta", inte bland de vanliga.
    @ViewBuilder
    private var tiersSection: some View {
        ForEach(Self.rarityTiers, id: \.key) { tier in
            let items = eggs
                .filter { $0.rarity == tier.key && !$0.collected }
                .sorted { ($0.unlocked ? 0 : 1) < ($1.unlocked ? 0 : 1) }
            if !items.isEmpty {
                zoneDivider(tier.label)
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(items) { item in
                        if item.unlocked {
                            EggTile(
                                egg: item.eggType,
                                selected: item.eggType == selectedEgg,
                                ordinal: (selectable.firstIndex { $0.eggType == item.eggType } ?? 0) + 1,
                                total: selectable.count,
                                palette: palette,
                                action: { onSelect(item.eggType) }
                            )
                        } else {
                            MysteryTile(palette: palette).id(item.id)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var collectedSection: some View {
        if !taken.isEmpty {
            zoneDivider("\(taken.count) av \(eggs.count) samlade")
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(taken, id: \.id) { entry in
                    CollectedTile(entry: entry, palette: palette)
                }
            }
        }
    }

    /// Den vaga avdelaren mellan zonerna.
    private func zoneDivider(_ label: String) -> some View {
        HStack(spacing: 10) {
            Rectangle().fill(palette.cardEdge).frame(height: 1)
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(palette.inkFaint)
                .fixedSize()
            Rectangle().fill(palette.cardEdge).frame(height: 1)
        }
        .padding(.top, 6)
        .padding(.bottom, 2)
    }
}

/// En plats som redan är fylld. Visar djuret med sin ram (om någon) och månaden det kom.
private struct CollectedTile: View {
    let entry: PetHistoryResponseDTO
    let palette: SeasonPalette

    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                if let name = PetImagesIOS.petImageName(for: entry.petType, growthStage: entry.finalGrowthStage),
                   let img = UIImage(named: name) {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFit()
                        .frame(width: entry.frame != nil ? 40 : 52, height: entry.frame != nil ? 40 : 52)
                } else {
                    Text("🐾").font(.title2).frame(width: 52, height: 52)
                }
                if let frameName = PetImagesIOS.frameImageName(entry.frame),
                   let frameImg = UIImage(named: frameName) {
                    Image(uiImage: frameImg)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 66, height: 66)
                }
            }
            Text(PetNameUtilsIOS.getPetNameSwedish(entry.petType))
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(palette.goodInk)
                .multilineTextAlignment(.center)
            Text(kqMonthName(entry.month).uppercased())
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(palette.goodInk.opacity(0.75))
        }
        .frame(maxWidth: .infinity, minHeight: 104)
        .padding(.vertical, 8)
        .padding(.horizontal, 4)
        .background(
            RoundedRectangle(cornerRadius: 15, style: .continuous).fill(palette.goodBg)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(palette.goodInk.opacity(0.35), lineWidth: 1.5)
        )
    }
}

/// Ett ägg som går att välja. `ordinal` finns bara för VoiceOver.
private struct EggTile: View {
    let egg: String
    let selected: Bool
    let ordinal: Int
    let total: Int
    let palette: SeasonPalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 0) {
                if let name = PetImagesIOS.eggImageName(for: egg),
                   let img = UIImage(named: name) {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 64, height: 64)
                } else {
                    Text("🥚").font(.title2).frame(width: 64, height: 64)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 92)
            .padding(.vertical, 10)
            .padding(.horizontal, 4)
            .background(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .fill(selected ? palette.tipBg : palette.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 15, style: .continuous)
                    .stroke(selected ? palette.accent : palette.cardEdge,
                            lineWidth: selected ? 2.5 : 1.5)
            )
        }
        .accessibilityLabel("Ägg \(ordinal) av \(total)")
        .accessibilityAddTraits(selected ? [.isSelected] : [])
        .buttonStyle(.plain)
    }
}

/// En oupptäckt plats. Ett tonat ägg med frågetecken tills `mystery_egg`-konsten finns —
/// den byts in automatiskt. Avslöjar varken djur eller sällsynthet.
private struct MysteryTile: View {
    let palette: SeasonPalette

    var body: some View {
        VStack(spacing: 0) {
            if let img = UIImage(named: "mystery_egg") {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
            } else {
                ZStack {
                    Text("🥚").font(.title2).opacity(0.35)
                    Text("?").font(.title3.weight(.bold)).foregroundStyle(palette.inkFaint)
                }
                .frame(width: 64, height: 64)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 92)
        .padding(.vertical, 10)
        .padding(.horizontal, 4)
        .background(
            RoundedRectangle(cornerRadius: 15, style: .continuous).fill(palette.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .stroke(palette.cardEdge, lineWidth: 1.5)
        )
    }
}
