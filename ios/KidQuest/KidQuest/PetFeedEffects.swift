import SwiftUI

/// Det synliga svaret på matningen: mätaren, maten som flyger och nivåhöjningen.
///
/// Barnen som testade sa tre saker -- att man inte ser att man matar, att man inte ser
/// progressen på nivån, och att det därför inte känns som att något händer. Alla tre
/// beskriver samma sak: en handling utan konsekvens på skärmen. Maten försvann ur en
/// siffra, XP:n gick någonstans osynligt, och när djuret väl växte sa ingen något.
///
/// Motsvarigheten på Android är `PetFeedEffects.kt`, och tiderna hålls lika med flit --
/// två plattformar som firar i olika takt känns som två olika appar.

/// Hur många XP den nuvarande nivån spänner över.
///
/// Trösklarna ({0, 10, 35, 70, 125} i `MemberXpProgress`) är medvetet ojämna, så en
/// mätare mot totalen kryper knappt i början av nivå 4. Spannet räknas därför ur DTO:n i
/// stället för ur en kopia av trösklarna: `xpInCurrentLevel` är hur långt in i nivån
/// barnet är och `xpForNextLevel` är hur mycket som fattas, så summan är nivåns egen
/// längd. En ändring av trösklarna på servern följer med hit utan att någon rör filen.
///
/// Noll betyder högsta nivån, där det inte finns någon nästa nivå att fylla mot.
func xpSpanFor(xpInCurrentLevel: Int, xpForNextLevel: Int) -> Int {
    xpInCurrentLevel + xpForNextLevel
}

// MARK: - Mätaren

/// Mätaren under djurets namn.
///
/// Den finns hela tiden och inte bara när något händer: ett barn som öppnar appen på
/// morgonen ska kunna se hur nära nästa stadie djuret är utan att först mata det.
struct XpMeter: View {
    let xpInLevel: Int
    let span: Int
    let level: Int
    var width: CGFloat = 172

    private var maxed: Bool { span <= 0 }
    private var filled: Double {
        maxed ? 1 : min(1, max(0, Double(xpInLevel) / Double(span)))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            ZStack(alignment: .leading) {
                // Mörkt spår och inte vitt. Bandet är en årstidsmålning, och ett vitt
                // spår försvann rakt in i vattenfallet -- man såg den fyllda delen men
                // inte hur mycket som återstod, vilket är halva informationen.
                Capsule()
                    .fill(.black.opacity(0.34))
                    .overlay(Capsule().stroke(.white.opacity(0.42), lineWidth: 1))
                Capsule()
                    .fill(
                        LinearGradient(
                            // Ljusare än accenten: mot ett mörkt spår behöver fyllningen
                            // lysa, inte matcha knappen.
                            colors: [Color(red: 0.85, green: 0.47, blue: 0.25),
                                     Color(red: 0.96, green: 0.69, blue: 0.39)],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .frame(width: width * filled)
            }
            .frame(width: width, height: 9)

            Text(label)
                .font(.system(size: 11, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(.white.opacity(0.9))
        }
        .animation(.easeInOut(duration: 0.62), value: filled)
    }

    private var label: String {
        if maxed { return "Största stadiet!" }
        // Full mätare betyder att tröskeln just passerats. Nivån är då redan uppräknad,
        // och "35 / 35 xp till nivå 5" hade varit fel i båda leden.
        if xpInLevel >= span { return "Nivå \(level) nådd!" }
        return "\(xpInLevel) / \(span) xp till nivå \(level + 1)"
    }
}

// MARK: - Maten som flyger

/// Ett stycke mat på väg från räknaren till djuret. Id:t skiljer två identiska bär.
struct FlyingBerry: Identifiable, Equatable {
    let id: Int
    let emoji: String
    /// Sparad per bär så att fyra bär inte ser ut som ett bär i fyra exemplar längs
    /// exakt samma bana.
    let drift: Double
    let spin: Double
}

/// Bågen. En rak linje läser som att något teleporteras; en båge läser som att någon
/// kastar.
///
/// `Animatable` och inte `withAnimation` på positionen direkt: lyftet är en sinusfunktion
/// av framstegen, och SwiftUI måste interpolera framstegen och räkna om läget för varje
/// bildruta -- annars interpoleras start- och slutpunkt rakt och bågen försvinner.
private struct BerryArc: ViewModifier, Animatable {
    var t: Double
    let from: CGPoint
    let to: CGPoint
    let drift: Double
    let spin: Double

    var animatableData: Double {
        get { t }
        set { t = newValue }
    }

    func body(content: Content) -> some View {
        let bow = sin(t * .pi)
        let x = from.x + (to.x - from.x) * t + drift * bow
        let y = from.y + (to.y - from.y) * t - bow * 52
        let scale: Double = {
            if t < 0.18 { return 0.6 + (t / 0.18) * 0.6 }
            if t > 0.82 { return 1.2 - ((t - 0.82) / 0.18) * 0.85 }
            return 1.2
        }()
        let alpha: Double = {
            if t < 0.12 { return t / 0.12 }
            if t > 0.88 { return 1 - (t - 0.88) / 0.12 }
            return 1
        }()

        content
            .scaleEffect(scale)
            .rotationEffect(.degrees(t * spin))
            .opacity(alpha)
            .position(x: x, y: y)
    }
}

struct BerryInFlight: View {
    let berry: FlyingBerry
    let from: CGPoint
    let to: CGPoint

    @State private var t: Double = 0

    var body: some View {
        Text(berry.emoji)
            // 20 punkter försvann mot höstmålningen. Bandet är ett detaljerat landskap
            // och maten ska läsas i förbifarten av ett barn, inte letas efter.
            .font(.system(size: 30))
            .shadow(color: .black.opacity(0.5), radius: 5, y: 2)
            .modifier(BerryArc(t: t, from: from, to: to, drift: berry.drift, spin: berry.spin))
            .onAppear {
                withAnimation(.easeInOut(duration: FeedAnimation.berryFlight)) { t = 1 }
            }
            .allowsHitTesting(false)
    }
}

// MARK: - Nivåhöjningen

/// Blänket, fanfaren och konfettin, som en enda vy med ett eget förlopp.
///
/// Ligger samlat för att de tre är ett ögonblick och inte tre: blänket ska hinna dölja
/// stadiebytet, fanfaren ska komma precis efter, och konfettin ska falla under båda.
struct LevelUpOverlay: View {
    let level: Int
    let petName: String
    let petCenter: CGPoint
    /// Bandets mått. Behövs för att placera banderollen absolut.
    let bandSize: CGSize
    let palette: SeasonPalette

    @State private var flash: Double = 0
    @State private var bannerIn = false
    @State private var confettiT: Double = 0

    private static let confetti: [Confetto] = (0..<26).map { i in
        Confetto(
            angle: (.pi * 2 * Double(i) / 26) + Double.random(in: 0...0.3),
            force: Double.random(in: 70...150),
            spin: Double.random(in: -360...360),
            color: [
                Color(red: 0.63, green: 0.27, blue: 0.12),
                Color(red: 0.85, green: 0.47, blue: 0.25),
                Color(red: 0.75, green: 0.53, blue: 0.38),
                Color(red: 0.29, green: 0.36, blue: 0.15),
                Color(red: 0.94, green: 0.75, blue: 0.44),
            ][i % 5],
            size: CGSize(width: Double.random(in: 5...8), height: Double.random(in: 8...12))
        )
    }

    var body: some View {
        ZStack {
            // Blänket. Konstens stadiebyte sker under det, så bytet läser som en
            // förvandling i stället för som att en bild ersattes med en annan.
            RadialGradient(
                colors: [.white.opacity(0.95), Color(red: 1, green: 0.94, blue: 0.86).opacity(0)],
                center: .center, startRadius: 0, endRadius: 220
            )
            .opacity(flash)
            .allowsHitTesting(false)

            ForEach(Array(Self.confetti.enumerated()), id: \.offset) { pair in
                Rectangle()
                    .fill(pair.element.color)
                    .frame(width: pair.element.size.width, height: pair.element.size.height)
                    .cornerRadius(1)
                    .modifier(
                        ConfettiPath(
                            t: confettiT,
                            origin: petCenter,
                            spec: pair.element,
                            delay: Double(pair.offset) * 0.012
                        )
                    )
                    .allowsHitTesting(false)
            }

            VStack(spacing: 1) {
                Text("Nivå \(level)!")
                    .font(.system(size: 17, weight: .bold))
                Text("\(petName) växte")
                    .font(.system(size: 12, weight: .medium))
                    .opacity(0.92)
            }
            .foregroundStyle(palette.onAccent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .padding(.horizontal, 14)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.accent)
            )
            .shadow(color: .black.opacity(0.34), radius: 12, y: 6)
            .padding(.horizontal, 14)
            .frame(width: max(40, bandSize.width - 28))
            .offset(y: bannerIn ? 0 : -16)
            .opacity(bannerIn ? 1 : 0)
            // .position och inte padding i en expanderad ram. Padding hedrades inte
            // härinne -- banderollen hamnade i notchen i stället för 54 punkter ner --
            // medan .position är absolut i förälderns rymd och redan bevisat av
            // konfettin i samma ZStack. .position anger MITTEN, och banderollen är
            // cirka 56 hög -- y=120 lägger överkanten på 92 och därmed under
            // djurväxlarens cirklar, som slutar vid 86. Vid 104 skar den den tredje.
            .position(x: bandSize.width / 2, y: 120)
            .allowsHitTesting(false)
        }
        .onAppear(perform: play)
    }

    /// Stegen ligger i en Task med pauser och inte som `.delay()` på animationerna.
    ///
    /// `.delay()` fördröjer kurvan men inte tillståndsändringen: satte man `flash = 1`
    /// och `flash = 0` i samma varv blev slutvärdet noll innan en enda bildruta ritats,
    /// och blänket, fanfaren och konfettin syntes aldrig. Två ändringar av samma
    /// egenskap i samma varv kollapsar till den sista.
    private func play() {
        Task { @MainActor in
            withAnimation(.easeOut(duration: 0.2)) { flash = 1 }
            withAnimation(.spring(response: 0.34, dampingFraction: 0.62)) { bannerIn = true }
            withAnimation(.linear(duration: 1.9)) { confettiT = 1 }

            try? await Task.sleep(for: .seconds(0.22))
            withAnimation(.easeIn(duration: 0.65)) { flash = 0 }

            try? await Task.sleep(for: .seconds(FeedAnimation.levelUpLength - 0.52))
            withAnimation(.easeIn(duration: 0.3)) { bannerIn = false }
        }
    }
}

struct Confetto {
    let angle: Double
    let force: Double
    let spin: Double
    let color: Color
    let size: CGSize
}

/// Ut och sedan ned: kraften bär utåt, tyngden tar över.
private struct ConfettiPath: ViewModifier, Animatable {
    var t: Double
    let origin: CGPoint
    let spec: Confetto
    let delay: Double

    var animatableData: Double {
        get { t }
        set { t = newValue }
    }

    func body(content: Content) -> some View {
        let p = max(0, min(1, (t - delay) / max(0.001, 1 - delay)))
        let x = origin.x + cos(spec.angle) * spec.force * p
        let y = origin.y + sin(spec.angle) * spec.force * p - 24 * p + 150 * p * p
        content
            .rotationEffect(.degrees(spec.spin * p))
            .opacity(p <= 0 ? 0 : (p > 0.7 ? 1 - (p - 0.7) / 0.3 : 1))
            .position(x: x, y: y)
    }
}

// MARK: - Sekvensen

/// Sekvensen från tryck till växt djur, plus de värden skärmen ska visa medan den går.
///
/// Överskuggningarna (`food`, `xpInLevel`, `level`, `stage`) är nil när inget pågår, och
/// då visas hostens egna värden. Under matningen äger den här klassen vad som står på
/// skärmen -- annars skulle hostens optimistiska nollställning av matsiffran slå ut
/// nedräkningen bär för bär, som är själva poängen.
///
/// Höjningen spelas bara som en direkt följd av en matning, aldrig ur inläst tillstånd.
/// Det är skillnaden mellan att fira för att barnet just gjorde något och att fira för
/// att skärmen råkade ritas om -- och det senare skulle hända vid varje omladdning.
@Observable
final class FeedAnimation {

    /// Delad med `BerryInFlight`, som animerar exakt så länge.
    static let berryFlight: Double = 0.62
    /// Mellanrummet mellan två bär som lyfter.
    static let berryStagger: Double = 0.13
    /// Hur länge blänket, fanfaren och konfettin varar.
    static let levelUpLength: Double = 2.4

    var berries: [FlyingBerry] = []
    var celebrating = false
    var petPulse: CGFloat = 1

    var food: Int?
    var xpInLevel: Int?
    var level: Int?
    var stage: Int?

    private var nextId = 0
    private(set) var running = false

    /// - Parameters:
    ///   - crossingBerry: index på det bär som tar barnet över tröskeln, eller nil om
    ///     ingen höjning sker. Räknas ur `xpForNextLevel`, som är hur många XP som
    ///     fattas -- alltså är det `(xpForNextLevel - 1)`:te bäret som korsar.
    @MainActor
    func run(
        amount: Int,
        emoji: String,
        span: Int,
        startXpInLevel: Int,
        startLevel: Int,
        startStage: Int,
        crossingBerry: Int?
    ) async {
        guard amount > 0, !running else { return }
        running = true
        food = amount
        xpInLevel = startXpInLevel
        level = startLevel
        stage = startStage

        await withTaskGroup(of: Void.self) { group in
            for i in 0..<amount {
                group.addTask { @MainActor in
                    try? await Task.sleep(for: .seconds(Double(i) * Self.berryStagger))

                    let berry = FlyingBerry(
                        id: self.nextId,
                        emoji: emoji,
                        drift: Double.random(in: -17...17),
                        spin: Double.random(in: 220...420)
                    )
                    self.nextId += 1
                    self.berries.append(berry)
                    self.food = max(0, (self.food ?? amount) - 1)

                    try? await Task.sleep(for: .seconds(Self.berryFlight))
                    self.berries.removeAll { $0.id == berry.id }

                    // Ett bär i taget, så fyra bär blir fyra saker som händer i stället
                    // för en siffra som byter värde. Mätaren stannar på fullt: att räkna
                    // förbi spannet hade visat "37 / 37" i stället för "35 / 35".
                    if span > 0 {
                        self.xpInLevel = min(span, (self.xpInLevel ?? startXpInLevel) + 1)
                    }
                    self.chew()

                    if i == crossingBerry {
                        try? await Task.sleep(for: .seconds(0.24))
                        // Namnraden visar nivån, och utan det här sa den "NIVÅ 3" i
                        // 2,4 sekunder medan fanfaren sa "Nivå 4!".
                        self.level = min(5, startLevel + 1)
                        self.stage = min(5, startStage + 1)
                        self.celebrating = true
                        self.grow()
                        try? await Task.sleep(for: .seconds(Self.levelUpLength))
                        self.celebrating = false
                    }
                }
            }
        }

        reset()
    }

    /// Den lilla studsen när ett bär landar.
    @MainActor
    private func chew() {
        withAnimation(.easeInOut(duration: 0.11)) { petPulse = 1.12 }
        withAnimation(.easeInOut(duration: 0.19).delay(0.11)) { petPulse = 1 }
    }

    /// Djuret dyker, växer förbi sin nya storlek och sätter sig.
    @MainActor
    private func grow() {
        withAnimation(.easeInOut(duration: 0.18)) { petPulse = 0.88 }
        withAnimation(.spring(response: 0.42, dampingFraction: 0.55).delay(0.18)) {
            petPulse = 1.24
        }
        withAnimation(.easeInOut(duration: 0.4).delay(0.6)) { petPulse = 1 }
    }

    /// Efter ett avbrutet eller avslutat flöde ska ingenting hänga kvar, och hostens egna
    /// värden ska gälla igen.
    @MainActor
    func reset() {
        berries = []
        celebrating = false
        petPulse = 1
        food = nil
        xpInLevel = nil
        level = nil
        stage = nil
        running = false
    }
}
