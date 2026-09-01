import SwiftUI

/// Barnets dag, ritad en gång.
///
/// Två skärmar visar samma sak: `ChildDashboardView` på barnets egen telefon, och
/// `ChildDashboardHost` när en förälder tittar under "Visa som barn". De måste vara
/// skilda i *data* -- barnets skärm läser med sin egen token, värdens anrop namnger
/// barnet i sökvägen -- men de får inte vara skilda i *utseende*, för då tittar
/// föräldern på något annat än barnet ser.
///
/// Så länge layouten låg i barnets vy var det precis vad som hände. Nu tar båda sina
/// vyer härifrån och skickar in data och handlingar.
///
/// Ordningen är vald efter vilken fråga ett barn öppnar appen med på en tisdagmorgon:
/// **vad ska jag göra nu?** Listan är störst och ligger på vitt så den går att läsa,
/// djuret bor i ett band ovanför. Bandet är kort så länge det finns uppgifter kvar och
/// växer när dagen är klar -- belöningen kommer efter arbetet, inte före.
///
/// Mörkt läge används inte. Det är föräldrarnas inställning, för det är de som sitter i
/// appen på kvällen; barnets skärm ska vara ljus och ha en årstid i den.
struct ChildDayLayout<Banner: View, Footer: View>: View {

    let childName: String
    let pet: PetResponseDTO?
    /// Skilt från "inget djur": själva anropet gick fel, så vi får inte påstå att barnet
    /// inte valt ägg och skicka det till äggväljaren.
    var petLoadFailed: Bool = false
    let level: Int
    let foodCount: Int
    let balance: Int?
    let tasks: [DailyChoreWithCompletionResponseDTO]
    var history: [PetHistoryResponseDTO] = []
    @Binding var viewingPast: PetHistoryResponseDTO?
    var isFeeding: Bool = false

    var onToggleTask: (DailyChoreWithCompletionResponseDTO) -> Void
    var onFeed: (Int) -> Void
    var onOpenWallet: () -> Void
    var onSelectEgg: () -> Void
    /// Nil döljer knappen. Barnets egen vy erbjuder den; värden gör det inte, för en
    /// förälder lägger till sysslor i sin egen vy.
    var onAddChore: (() -> Void)?

    @ViewBuilder var banner: () -> Banner
    @ViewBuilder var footer: () -> Footer

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    private var doneCount: Int { tasks.filter(\.completed).count }
    private var allDone: Bool { !tasks.isEmpty && doneCount == tasks.count }
    private var isPast: Bool { viewingPast != nil }

    /// Måtten är valda så att listan alltid börjar ovanför skärmens mitt i det korta
    /// läget, och så att djuret får plats att stå fram i det stora.
    private var bandHeight: CGFloat { allDone && !isPast ? 400 : 258 }

    var body: some View {
        ZStack {
            palette.pageBg.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    band
                    VStack(spacing: 14) {
                        banner()
                        if isPast {
                            backToNowCard
                        } else {
                            tasksCard
                            feedButton
                        }
                        footer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 14)
                    .padding(.bottom, 28)
                }
            }
            .ignoresSafeArea(edges: .top)
        }
        .environment(\.seasonPalette, palette)
    }

    // MARK: - Bandet

    private var band: some View {
        ZStack(alignment: .bottomLeading) {
            if let shown = shownPet {
                PetVisual(
                    petType: shown.type,
                    growthStage: shown.stage,
                    cornerRadius: 0,
                    // Djuret kryper åt sidan när det finns arbete kvar och kliver fram
                    // i mitten när dagen är klar.
                    scale: allDone && !isPast ? 0.82 : 0.52,
                    alignment: allDone && !isPast ? .bottom : .bottomTrailing
                )
                .frame(height: bandHeight)
                .clipped()
            } else {
                seasonBackdrop
            }

            // Läsbarhet: nedre kanten tonar in i sidans färg, toppen mörknar så att
            // raden med knappar syns mot vilken årstid som helst.
            LinearGradient(
                colors: [
                    .black.opacity(0.34), .clear, .clear,
                    palette.pageBg.opacity(0.55), palette.pageBg,
                ],
                startPoint: .top, endPoint: .bottom
            )
            .frame(height: bandHeight)
            .allowsHitTesting(false)

            bandLabels
        }
        .frame(height: bandHeight)
        .overlay(alignment: .top) { bandTopRow }
        .animation(.easeInOut(duration: 0.45), value: allDone)
    }

    /// Årstiden utan djur, för månaden innan ett ägg är valt. Bakgrunden ska finnas där
    /// från första sekunden -- den är halva känslan.
    @ViewBuilder
    private var seasonBackdrop: some View {
        if let name = PetImagesIOS.seasonalBackgroundName() {
            Image(name)
                .resizable()
                .scaledToFill()
                .frame(height: bandHeight)
                .clipped()
        } else {
            palette.headerTop.frame(height: bandHeight)
        }
    }

    private var bandTopRow: some View {
        HStack(alignment: .top, spacing: 8) {
            petSwitcher
            Spacer(minLength: 8)
            walletChip
        }
        .padding(.horizontal, 14)
        .padding(.top, 52)
    }

    private var bandLabels: some View {
        VStack(alignment: .leading, spacing: 7) {
            if let past = viewingPast {
                Text(monthLabel(year: past.year, month: past.month).uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.1)
                    .foregroundStyle(.white.opacity(0.9))
                Text(PetNameUtilsIOS.getPetNameSwedish(past.petType))
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
            } else if let pet {
                Text("\(petDisplayName(pet)) · NIVÅ \(level)".uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.1)
                    .foregroundStyle(.white.opacity(0.92))
                if allDone {
                    Text("Allt klart idag!")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.white)
                }
                if foodCount > 0 {
                    foodChip
                }
            }
        }
        .shadow(color: .black.opacity(0.45), radius: 5, y: 1)
        .padding(.horizontal, 18)
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var foodChip: some View {
        HStack(spacing: 5) {
            Text(PetFoodUtilsIOS.emoji(for: shownPet?.type))
                .font(.system(size: 13))
            Text("\(foodCount) mat att ge")
                .font(.caption.weight(.bold))
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 6)
        .background(Capsule().fill(.white.opacity(0.94)))
        .foregroundStyle(palette.tipStrong)
    }

    /// Saldot, och vägen till plånboken. Ett tryck och inte ett kort: bandet ska inte
    /// konkurrera med listan om uppmärksamheten.
    private var walletChip: some View {
        Button(action: onOpenWallet) {
            HStack(spacing: 5) {
                Image(systemName: "wallet.bifold.fill")
                    .font(.system(size: 12, weight: .semibold))
                Text(balance.map { "\($0) kr" } ?? "Plånbok")
                    .font(.caption.weight(.bold))
                Image(systemName: "chevron.right")
                    .font(.system(size: 9, weight: .bold))
                    .opacity(0.6)
            }
            .padding(.horizontal, 11)
            .padding(.vertical, 7)
            .background(Capsule().fill(.white.opacity(0.92)))
            .foregroundStyle(palette.accent)
        }
        .buttonStyle(.plain)
    }

    /// Raden med insamlade djur, dagens först. Visas bara när det finns något att växla
    /// mellan -- en enda cirkel är ingen växling, bara en prick.
    @ViewBuilder
    private var petSwitcher: some View {
        if !history.isEmpty, let current = pet {
            HStack(spacing: 6) {
                petCircle(type: current.petType, stage: current.growthStage, active: !isPast) {
                    viewingPast = nil
                }
                ForEach(history.prefix(5)) { past in
                    petCircle(
                        type: past.petType,
                        stage: past.finalGrowthStage,
                        active: viewingPast?.id == past.id
                    ) { viewingPast = past }
                }
            }
        }
    }

    private func petCircle(
        type: String, stage: Int, active: Bool, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(.white.opacity(0.92))
                if let name = PetImagesIOS.petImageName(for: type, growthStage: stage) {
                    Image(name).resizable().scaledToFit().padding(2)
                }
            }
            .frame(width: 36, height: 36)
            .overlay(
                Circle().stroke(
                    active ? palette.warnStrong : .white.opacity(0.9),
                    lineWidth: active ? 2.5 : 1.5
                )
            )
            .opacity(active ? 1 : 0.72)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(PetNameUtilsIOS.getPetNameSwedish(type))
    }

    // MARK: - Uppgifterna

    private var tasksCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Dagens uppgifter")
                    .font(.system(size: 21, weight: .bold))
                    .foregroundStyle(palette.ink)
                Spacer()
                if !tasks.isEmpty {
                    Text("\(doneCount) / \(tasks.count)")
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(allDone ? palette.goodInk : palette.inkSoft)
                }
            }
            .padding(.bottom, 10)

            if tasks.isEmpty {
                emptyState
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(tasks.enumerated()), id: \.element.chore.id) { index, task in
                        taskRow(task)
                        if index < tasks.count - 1 {
                            Divider().overlay(palette.cardEdge.opacity(0.7))
                        }
                    }
                }
                .padding(.horizontal, 14)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .stroke(palette.cardEdge, lineWidth: 1)
                )
            }

            if let onAddChore {
                Button("+ Lägg till en syssla", action: onAddChore)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.inkSoft)
                    .padding(.top, 10)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }

    private func taskRow(_ task: DailyChoreWithCompletionResponseDTO) -> some View {
        Button {
            onToggleTask(task)
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(task.completed ? palette.goodInk : palette.pageBg)
                    if task.completed {
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .heavy))
                            .foregroundStyle(.white)
                    } else {
                        Circle().stroke(palette.cardEdge, lineWidth: 2)
                    }
                }
                .frame(width: 28, height: 28)

                Text(task.chore.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(task.completed ? palette.inkFaint : palette.ink)
                    .strikethrough(task.completed, color: palette.inkFaint)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if task.chore.xpPoints > 0 {
                    Text("+\(task.chore.xpPoints) mat")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(task.completed ? palette.inkFaint : palette.tipStrong)
                }
            }
            .padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: 6) {
            if petLoadFailed {
                Text("Kunde inte läsa djuret")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.ink)
                Text("Försök igen om en stund. Inget har gått förlorat.")
                    .font(.subheadline)
                    .foregroundStyle(palette.inkSoft)
            } else if pet == nil {
                Text("Välj ett ägg först")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.ink)
                Text("Då får du ett djur att ta hand om.")
                    .font(.subheadline)
                    .foregroundStyle(palette.inkSoft)
                Button("Välj ägg", action: onSelectEgg)
                    .font(.body.weight(.bold))
                    .foregroundStyle(palette.onAccent)
                    .padding(.horizontal, 22).padding(.vertical, 12)
                    .background(Capsule().fill(palette.accent))
                    .padding(.top, 4)
            } else {
                Text("Inga uppgifter idag")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.ink)
                Text("Njut av dagen!")
                    .font(.subheadline)
                    .foregroundStyle(palette.inkSoft)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 26)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
        )
    }

    // MARK: - Matningen

    /// Ligger i flödet under listan och inte fastlåst längst ner. Samma beslut som i
    /// föräldravyn, av samma skäl: en knapp som alltid ligger över innehållet gör
    /// skärmen trängre än den behöver vara.
    @ViewBuilder
    private var feedButton: some View {
        if let pet {
            VStack(spacing: 8) {
                Button {
                    onFeed(foodCount)
                } label: {
                    Text(foodCount > 0
                         ? "Mata \(petDisplayName(pet)) med \(foodCount) mat"
                         : "Ingen mat att ge än")
                        .font(.system(size: 17, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .foregroundStyle(foodCount > 0 ? palette.onAccent : palette.inkFaint)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(foodCount > 0 ? palette.accent : palette.outlineBg)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(foodCount > 0 ? .clear : palette.outlineEdge, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .disabled(foodCount == 0 || isFeeding)

                if foodCount > 1 {
                    Button("Mata bara 1") { onFeed(1) }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.inkSoft)
                        .disabled(isFeeding)
                }
            }
        }
    }

    private var backToNowCard: some View {
        VStack(spacing: 10) {
            Text("Det här djuret är färdigväxt")
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.ink)
            Text("Du kan inte mata det längre, men det stannar i din samling.")
                .font(.subheadline)
                .foregroundStyle(palette.inkSoft)
                .multilineTextAlignment(.center)
            Button("Tillbaka till nu") { viewingPast = nil }
                .font(.body.weight(.bold))
                .foregroundStyle(palette.onAccent)
                .padding(.horizontal, 22).padding(.vertical, 12)
                .background(Capsule().fill(palette.accent))
        }
        .frame(maxWidth: .infinity)
        .padding(22)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(palette.surface)
        )
    }

    // MARK: - Härledda värden

    private var shownPet: (type: String, stage: Int)? {
        if let past = viewingPast { return (past.petType, past.finalGrowthStage) }
        if let pet { return (pet.petType, pet.growthStage) }
        return nil
    }

    private func petDisplayName(_ pet: PetResponseDTO) -> String {
        let given = pet.name?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let given, !given.isEmpty { return given }
        return PetNameUtilsIOS.getPetNameSwedish(pet.petType)
    }

    private func monthLabel(year: Int, month: Int) -> String {
        let names = ["januari", "februari", "mars", "april", "maj", "juni",
                     "juli", "augusti", "september", "oktober", "november", "december"]
        return "\(names[max(0, min(11, month - 1))]) \(year)"
    }
}
