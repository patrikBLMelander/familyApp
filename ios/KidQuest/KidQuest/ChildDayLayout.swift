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
    /// Hur långt in i nivån barnet är, och hur mycket som fattas. Båda finns i
    /// XpProgressResponseDTO och låg oanvända här -- nivån var en textsträng, så ett barn
    /// på 17 av 25 xp såg exakt samma skärm som ett barn på 1 av 25.
    var xpInLevel: Int = 0
    var xpForNext: Int = 0
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

    /// Startar matningen av sig själv strax efter att vyn visats. Bara harnesket sätter
     /// den: höjningen spelas med flit aldrig ur inläst tillstånd, och simulatorn tar
     /// inte emot tryck, så det finns annars inget sätt att se sekvensen alls.
    var harnessAutoFeed: Bool = false

    @ViewBuilder var banner: () -> Banner
    @ViewBuilder var footer: () -> Footer

    /// Matningens synliga del. Klassen äger vad som står på skärmen medan sekvensen
    /// går: hosten nollställer matsiffran optimistiskt på en gång, och utan en
    /// överskuggning här hade det slagit ut nedräkningen bär för bär, som är poängen.
    @State private var anim = FeedAnimation()
    /// Brickornas läge i scenens koordinater, alltså varifrån maten lyfter.
    @State private var tilesCenter: CGPoint?
    @State private var harnessFired = false

    private var palette: SeasonPalette { SeasonTheme.current(dark: false) }

    // Under matningen gäller animationens värden, annars hostens.
    private var shownFood: Int { anim.food ?? foodCount }
    private var shownXpInLevel: Int { anim.xpInLevel ?? xpInLevel }
    private var shownLevel: Int { anim.level ?? level }
    private var xpSpan: Int { xpSpanFor(xpInCurrentLevel: xpInLevel, xpForNextLevel: xpForNext) }

    private var doneCount: Int { tasks.filter(\.completed).count }
    private var allDone: Bool { !tasks.isEmpty && doneCount == tasks.count }
    private var isPast: Bool { viewingPast != nil }

    /// Måtten är valda så att listan alltid börjar ovanför skärmens mitt i det korta
    /// läget, och så att djuret får plats att stå fram i det stora.
    private var bandHeight: CGFloat { allDone && !isPast ? 400 : 258 }

    var body: some View {
        harnessBody
    }

    /// Autostarten ligger på roten och i en ostrukturerad Task med flit.
    ///
    /// Den satt först som .task på bandet, och blev flakig: .task avbryts när vyn ritas
    /// om, och eftersom Task.sleep då kastar och try? slukar felet uteblev matningen
    /// ungefär varannan gång. En Task som inte hänger på vyns livstid gör den
    /// förutsägbar, och det är just förutsägbarhet ett harness finns för.
    @ViewBuilder
    private var harnessBody: some View {
        content
            .onAppear {
                guard harnessAutoFeed, !harnessFired else { return }
                harnessFired = true
                Task { @MainActor in
                    // Pausen räcker för att lungan ska ha mätt sitt läge -- utan en
                    // punkt att flyga från hoppas bären över.
                    try? await Task.sleep(for: .seconds(1.2))
                    startFeed(shownFood)
                }
            }
    }

    private var content: some View {
        ZStack {
            palette.pageBg.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    scen
                    VStack(spacing: 14) {
                        banner()
                        if isPast {
                            backToNowCard
                        } else {
                            tasksCard
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

    // MARK: - Scenen

    /// Bandet och matremsan i en gemensam behållare.
    ///
    /// De två ligger tillsammans för att maten flyger MELLAN dem -- från en bricka i
    /// remsan upp till djuret i bandet. Bandet klipper sitt eget innehåll, så ett frö som
    /// bara låg i bandet hade kapats vid underkanten. Flyglagret ligger därför här, medan
    /// konfettin blir kvar inne i bandet där den ska klippas.
    private var scen: some View {
        VStack(spacing: 10) {
            band
            // Utan djur finns ingen mat att ge, men det ska finnas en väg till ägget --
            // och den låg förut bara bakom en tom uppgiftslista.
            if !isPast, pet == nil, !petLoadFailed {
                ChooseEggStrip(palette: palette, onSelectEgg: onSelectEgg)
                    .padding(.horizontal, 13)
            }
            // Ett tidigare djur går inte att mata, och en misslyckad hämtning får inte
            // visa en tom remsa som om barnet vore utan mat.
            if !isPast, let pet, !petLoadFailed {
                FoodStrip(
                    foodCount: shownFood,
                    emoji: PetFoodUtilsIOS.emoji(for: shownPet?.type),
                    petName: petDisplayName(pet),
                    // Under firandet ligger allt stilla: att mata in i en pågående
                    // nivåhöjning gör två saker samtidigt av det som ska vara ett
                    // ögonblick.
                    enabled: !anim.celebrating,
                    palette: palette,
                    onFeedOne: { feedOne() },
                    onFeedAll: { startFeed(shownFood) },
                    onTilesFrame: { tilesCenter = $0 }
                )
                .padding(.horizontal, 13)
            }
        }
        .overlay { berryLayer }
        .coordinateSpace(.named(kqScenSpace))
    }

    /// Maten i luften, ovanpå både bandet och remsan.
    private var berryLayer: some View {
        GeometryReader { geo in
            let bigPet = allDone && !isPast
            let petScale: CGFloat = bigPet ? 0.82 : 0.52
            let boxW = geo.size.width * petScale
            // Höjden räknas mot BANDET och inte mot scenen: djuret bor i bandet, och
            // scenen är högre eftersom remsan ligger under.
            let boxH = bandHeight * petScale
            let petCenter = CGPoint(
                x: bigPet ? geo.size.width / 2 : geo.size.width - boxW / 2,
                y: bandHeight - boxH / 2
            )
            ZStack {
                if let from = tilesCenter {
                    ForEach(anim.berries) { berry in
                        BerryInFlight(berry: berry, from: from, to: petCenter)
                    }
                }
            }
        }
        .allowsHitTesting(false)
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
                    alignment: allDone && !isPast ? .bottom : .bottomTrailing,
                    // Bara djuret pulsar. Skalar man hela PetVisual zoomar landskapet.
                    petScaleMultiplier: anim.petPulse
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
        // Effekterna ovanpå allt i bandet, och klippta till det: konfettin faller nedåt
        // och hade annars ritats ner över uppgiftskortet, vilket läser som en bugg och
        // inte som ett firande.
        .overlay { celebrationLayer }
        .clipped()
        .animation(.easeInOut(duration: 0.45), value: allDone)
    }

    /// Bären i luften och nivåhöjningen.
    ///
    /// GeometryReader för att djuret flyttar sig: det står i nederkant till höger på 0,52
    /// normalt och i mitten på 0,82 när dagen är klar, i ett band som är 400 eller 258
    /// punkter. Målet kan alltså inte hårdkodas. scaledToFit centrerar konsten i sin box,
    /// så boxens mitt är konstens mitt -- det är dit maten ska.
    @ViewBuilder
    /// Blänket, fanfaren och konfettin -- klippt till bandet.
    ///
    /// Konfettin faller och ska sluta vid bandets kant; maten flyger uppåt och ska inte.
    /// Därför ligger de i olika lager.
    private var celebrationLayer: some View {
        GeometryReader { geo in
            let bigPet = allDone && !isPast
            let petScale: CGFloat = bigPet ? 0.82 : 0.52
            let boxW = geo.size.width * petScale
            let boxH = geo.size.height * petScale
            let petCenter = CGPoint(
                x: bigPet ? geo.size.width / 2 : geo.size.width - boxW / 2,
                y: geo.size.height - boxH / 2
            )

            ZStack {
                if anim.celebrating, let pet {
                    LevelUpOverlay(
                        level: shownLevel,
                        petName: petDisplayName(pet),
                        petCenter: petCenter,
                        bandSize: geo.size,
                        palette: palette
                    )
                }
            }
        }
        .allowsHitTesting(false)
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
                Text("\(petDisplayName(pet)) · NIVÅ \(shownLevel)".uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.1)
                    .foregroundStyle(.white.opacity(0.92))
                // Mätaren finns hela tiden och inte bara när något händer: ett barn som
                // öppnar appen på morgonen ska se hur nära nästa stadie djuret är utan
                // att först mata det.
                XpMeter(xpInLevel: shownXpInLevel, span: xpSpan, level: shownLevel)
                if allDone {
                    Text("Allt klart idag!")
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.white)
                }
                // Matlungan låg här förut och sa "5 mat att ge". Remsan under bandet
                // visar samma sak och går dessutom att trycka på, så två platser hade
                // sagt samma sak och bara den ena gjort något.
            }
        }
        .shadow(color: .black.opacity(0.45), radius: 5, y: 1)
        .padding(.horizontal, 18)
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
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
                // Knappen låg här förut och var enda vägen in -- alltså osynlig för
                // varje barn som hade sysslor. Den bor i remsan ovanför nu, och två
                // knappar för samma sak på en tom skärm läser som en dubblett.
                Text("Knappen ligger i remsan ovanför.")
                    .font(.subheadline)
                    .foregroundStyle(palette.inkSoft)
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


    /// Startar nätanropet och animationen samtidigt.
    ///
    /// Hosten äger anropet och sin egen optimistiska nollställning; den här vyn äger vad
    /// som står på skärmen under sekvensen. Höjningen spelas bara härifrån och aldrig ur
    /// inläst tillstånd -- annars hade den firat vid varje omladdning.
    /// Ett tryck på en bricka i remsan: ett stycke mat.
    private func feedOne() {
        guard shownFood > 0, !anim.celebrating, let pet else { return }
        let emoji = PetFoodUtilsIOS.emoji(for: shownPet?.type)
        onFeed(1)
        Task { @MainActor in
            await anim.tapOne(
                emoji: emoji,
                span: xpSpan,
                hostFood: shownFood,
                hostXpInLevel: xpInLevel,
                hostLevel: level,
                hostStage: pet.growthStage
            )
        }
    }

    private func startFeed(_ amount: Int) {
        guard amount > 0, !anim.running, let pet else { return }
        let emoji = PetFoodUtilsIOS.emoji(for: shownPet?.type)
        // xpForNext är hur många XP som FATTAS, så det är det (xpForNext - 1):te bäret
        // som korsar tröskeln. Noll betyder högsta nivån, där ingen höjning finns.
        let crossing = (1...max(1, amount)).contains(xpForNext) ? xpForNext - 1 : nil
        onFeed(amount)
        Task { @MainActor in
            await anim.run(
                amount: amount,
                emoji: emoji,
                span: xpSpan,
                startXpInLevel: xpInLevel,
                startLevel: level,
                startStage: pet.growthStage,
                crossingBerry: crossing
            )
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
        // anim.stage under höjningen: bytet sker under blänket, så det läser som att
        // djuret växer och inte som att en bild ersattes med en annan.
        if let pet { return (pet.petType, anim.stage ?? pet.growthStage) }
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
