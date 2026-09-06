import SwiftUI

/// Äggväljaren som en samlingstavla.
///
/// Var en rak lista med fjorton fullbreda kort under varandra. Två saker gjorde att den
/// behövde göras om: den var lång att bläddra igenom, och när dubbletter uteslöts krympte
/// den varje månad utan att något förklarade vart äggen tog vägen.
///
/// Tavlan svarar på båda. De arter barnet redan samlat ligger kvar på sina platser -- som
/// djur, inte som ägg, med månaden de kom under sig -- och går inte att välja. Resten är
/// ägg. Rutan säger fortfarande "detta är allt som finns", men som en samling att fylla
/// och inte som ett lager som töms: ett barn med tre djur ser tre det har, inte elva det
/// saknar.
struct EggCollectionBoard: View {
    let eggTypes: [String]
    let history: [PetHistoryResponseDTO]
    let selectedEgg: String?
    let palette: SeasonPalette
    let onSelect: (String) -> Void

    /// Ägget är nyckeln, inte arten. Historiken bär `selectedEggType`, så uppslagningen
    /// behöver ingen kopia av backendens `EGG_TO_PET_MAP` -- och en kopia av den kartan
    /// är precis vad som gjorde att lejonet och hajen saknade namn i somras.
    private var collected: [String: PetHistoryResponseDTO] {
        Dictionary(history.map { ($0.selectedEggType.lowercased(), $0) },
                   uniquingKeysWith: { first, _ in first })
    }

    private let columns = [
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
    ]

    /// Valbara ägg först, samlade djur sist. Blandade låg de samlade som luckor mitt i
    /// det barnet faktiskt ska välja bland, och bröt läsrytmen för ingenting -- de går
    /// inte att välja. Samlingen är något att titta på efteråt, inte något att skanna
    /// förbi under tiden.
    private var available: [String] {
        eggTypes.filter { collected[$0.lowercased()] == nil }
    }

    private var taken: [PetHistoryResponseDTO] {
        eggTypes.compactMap { collected[$0.lowercased()] }
    }

    var body: some View {
        VStack(spacing: 8) {
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(available, id: \.self) { egg in
                    EggTile(
                        egg: egg,
                        selected: egg == selectedEgg,
                        palette: palette,
                        action: { onSelect(egg) }
                    )
                }
            }

            if !taken.isEmpty {
                // Den vaga avdelaren. En hårfin linje och en rad som säger vad som
                // följer -- tillräckligt för att ögat ska förstå att listan tar slut här,
                // utan att bli en rubrik som konkurrerar med äggen ovanför.
                HStack(spacing: 10) {
                    Rectangle().fill(palette.cardEdge).frame(height: 1)
                    Text("\(taken.count) av \(eggTypes.count) samlade")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(palette.inkFaint)
                        .fixedSize()
                    Rectangle().fill(palette.cardEdge).frame(height: 1)
                }
                .padding(.top, 6)
                .padding(.bottom, 2)

                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(taken, id: \.id) { entry in
                        CollectedTile(entry: entry, palette: palette)
                    }
                }
            }
        }
    }
}

/// En plats som redan är fylld. Visar djuret och månaden det kom.
private struct CollectedTile: View {
    let entry: PetHistoryResponseDTO
    let palette: SeasonPalette

    var body: some View {
        VStack(spacing: 2) {
            if let name = PetImagesIOS.petImageName(for: entry.petType, growthStage: entry.finalGrowthStage),
               let img = UIImage(named: name) {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 52, height: 52)
            } else {
                Text("🐾").font(.title2).frame(width: 52, height: 52)
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

/// Ett ägg som går att välja.
private struct EggTile: View {
    let egg: String
    let selected: Bool
    let palette: SeasonPalette
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                if let name = PetImagesIOS.eggImageName(for: egg),
                   let img = UIImage(named: name) {
                    Image(uiImage: img)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 52, height: 52)
                } else {
                    Text("🥚").font(.title2).frame(width: 52, height: 52)
                }
                Text(EggNames.label(for: egg))
                    .font(.system(size: 11, weight: .semibold))
                    // Årstidens färg och inte systemets blå: resten av barnvyn är höst
                    // eller vinter, och väljaren var det enda stället som var blå.
                    .foregroundStyle(selected ? palette.accent : palette.inkSoft)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 104)
            .padding(.vertical, 8)
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
        .buttonStyle(.plain)
    }
}
