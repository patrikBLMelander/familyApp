import SwiftUI

/// Månaden som tog slut, och djuret som ska sparas.
///
/// `tasks` är nil när XP-historiken inte gick att hämta. Raden utelämnas då hellre än att
/// visa en nolla -- "0 sysslor avbockade" är en anklagelse, inte en sammanfattning.
struct MonthFarewellData: Equatable {
    let entry: PetHistoryResponseDTO
    let petName: String
    let tasks: Int?
}

/// Avskedet vid månadsskiftet.
///
/// `XpService.monthlyReset()` flyttar djuret till pet_history klockan noll den första och
/// raderar det. Fram till nu innebar det att en månads avbockade sysslor slutade i att
/// djuret var borta nästa gång barnet tittade -- samma tystnad som nivåhöjningen hade
/// innan, fast större, eftersom det är hela månaden som avslutas.
///
/// Lagret ritar sin egen samlingsplats överst i stället för att sikta på barnvyns riktiga
/// ring. Den ligger under ett mörkläggande lager och skulle behöva mätas genom det; ett
/// eget exemplar på samma plats gör flykten självständig och kan inte hamna fel för att
/// något under råkade flytta sig.
///
/// Spelas en gång per barn och månad, aldrig ur inläst tillstånd -- samma regel som
/// nivåhöjningen, av samma skäl. Android har samma vy i `MonthFarewell.kt`, med samma
/// tider.
struct MonthFarewell: View {
    let data: MonthFarewellData
    let palette: SeasonPalette
    let onSaved: () -> Void
    /// Trycker knappen av sig själv strax efter att vyn visats. Bara harnesket:
    /// simulatorn tar inte emot tryck, och flykten är hela poängen med sekvensen.
    var harnessAutoSave: Bool = false

    /// 0 = djuret står stilla i mitten, 1 = det har landat i samlingen.
    @State private var flight: Double = 0
    @State private var landed = false
    @State private var saving = false

    private var petImage: UIImage? {
        PetImagesIOS.petImageName(for: data.entry.petType, growthStage: data.entry.finalGrowthStage)
            .flatMap { UIImage(named: $0) }
    }

    /// Skärmens mått, mätta utan att röra layouten.
    ///
    /// Först låg en GeometryReader runt hela vyn. Den ger sitt innehåll full storlek utan
    /// att centrera, så bakgrunden zoomades in och texterna sköts ut ur bild. En
    /// bakgrundsmätare rör ingenting.
    @State private var screen: CGSize = .zero

    var body: some View {
        content(screen)
            .background(
                GeometryReader { geo in
                    Color.clear
                        .onAppear { screen = geo.size }
                        .onChange(of: geo.size) { _, ny in screen = ny }
                }
            )
    }

    /// Sträckan räknas ur skärmens mått och inte ur hårdkodade punkter.
    ///
    /// Först stod det -130 och -300, vilket var gissat: draken tonade ut mitt på skärmen
    /// i stället för att landa i samlingen. Djuret sitter ovanför textblocket, som är
    /// ungefär 230 punkter plus 30 i botten, och samlingsplatsens mitt ligger på (32, 70).
    private func flightTarget(_ size: CGSize) -> CGSize {
        // Innan måtten kommit in flyger ingenting; första bildrutan hinner mäta.
        guard size.width > 0, size.height > 0 else { return .zero }
        let petCenterY = size.height - 30 - 230 - 105
        return CGSize(width: 32 - size.width / 2, height: 70 - petCenterY)
    }

    @ViewBuilder
    private func content(_ size: CGSize) -> some View {
        ZStack {
            if let name = PetImagesIOS.seasonalBackgroundName(), let bg = UIImage(named: name) {
                Image(uiImage: bg)
                    .resizable()
                    .scaledToFill()
                    .ignoresSafeArea()
            }
            LinearGradient(
                colors: [.black.opacity(0.62), .black.opacity(0.42), .black.opacity(0.78)],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            // Samlingsplatsen, dit djuret ska. Ett eget exemplar; se typens kommentar.
            Circle()
                .fill(.white.opacity(landed ? 0.92 : 0.24))
                .overlay(
                    Circle().stroke(landed ? palette.warnStrong : .white.opacity(0.5),
                                    lineWidth: landed ? 2.5 : 1.5)
                )
                .overlay {
                    if landed, let img = petImage {
                        Image(uiImage: img).resizable().scaledToFit().padding(3)
                    }
                }
                .frame(width: 36, height: 36)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(.leading, 14)
                .padding(.top, 52)

            VStack(spacing: 5) {
                Spacer()

                // Djuret. Flyger uppåt vänster när knappen trycks och krymper till
                // samlingsplatsens storlek på vägen.
                Group {
                    if let img = petImage {
                        Image(uiImage: img).resizable().scaledToFit()
                    }
                }
                .frame(width: 210, height: 210)
                .modifier(FarewellFlight(t: flight, target: flightTarget(size)))

                Text("\(kqMonthName(data.entry.month).uppercased()) ÄR SLUT")
                    .font(.system(size: 11, weight: .bold))
                    .tracking(1.2)
                    .foregroundStyle(.white.opacity(0.82))

                Text("\(data.petName) blev \(stageWord(data.entry.finalGrowthStage))")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)

                // Fem streck, ett per stadie. Visar hur långt det kom utan att någon
                // behöver läsa en siffra.
                HStack(spacing: 5) {
                    ForEach(0..<5, id: \.self) { i in
                        Capsule()
                            .fill(i < data.entry.finalGrowthStage
                                  ? AnyShapeStyle(LinearGradient(
                                        colors: [Color(red: 0.85, green: 0.47, blue: 0.25),
                                                 Color(red: 0.96, green: 0.69, blue: 0.39)],
                                        startPoint: .leading, endPoint: .trailing))
                                  : AnyShapeStyle(Color.white.opacity(0.28)))
                            .frame(width: 28, height: 5)
                    }
                }
                .padding(.top, 8)

                Text(summary)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.9))
                    .padding(.top, 4)

                Button {
                    guard !saving else { return }
                    saving = true
                } label: {
                    Text("Spara \(data.petName) i samlingen")
                        .font(.system(size: 16, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .foregroundStyle(palette.onAccent)
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(palette.accent.opacity(saving ? 0.7 : 1))
                        )
                }
                .buttonStyle(.plain)
                .disabled(saving)
                .padding(.top, 12)

                Text("Du kan alltid titta på den igen")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                    .padding(.top, 2)
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 30)
            .shadow(color: .black.opacity(0.6), radius: 10, y: 1)
        }
        .task {
            guard harnessAutoSave else { return }
            try? await Task.sleep(for: .seconds(1.6))
            saving = true
        }
        .onChange(of: saving) { _, nu in
            guard nu else { return }
            Task { @MainActor in
                withAnimation(.easeInOut(duration: 1.0)) { flight = 1 }
                try? await Task.sleep(for: .seconds(1.0))
                landed = true
                // Ett andetag med djuret på plats innan väljaren tar över. Utan den
                // hinner ingen se vart det tog vägen, vilket är hela poängen med flykten.
                try? await Task.sleep(for: .seconds(0.7))
                onSaved()
            }
        }
    }

    private var summary: String {
        var s = "Nivå \(data.entry.finalGrowthStage) av 5"
        if let tasks = data.tasks, tasks > 0 { s += " · \(tasks) sysslor avbockade" }
        return s
    }

    /// "Fullvuxen" när det nådde toppen, annars något mildare.
    private func stageWord(_ stage: Int) -> String {
        switch stage {
        case 5: return "fullvuxen"
        case 4: return "nästan fullvuxen"
        case 3: return "stor"
        case 2: return "lite större"
        default: return "en liten unge"
        }
    }
}

/// Bågen upp i samlingen.
///
/// `Animatable` och inte `withAnimation` på lägena direkt: lyftet är en sinusfunktion av
/// framstegen, och SwiftUI måste interpolera framstegen och räkna om läget för varje
/// bildruta -- annars interpoleras start och slut rakt och bågen försvinner.
private struct FarewellFlight: ViewModifier, Animatable {
    var t: Double
    let target: CGSize

    var animatableData: Double {
        get { t }
        set { t = newValue }
    }

    func body(content: Content) -> some View {
        let bow = sin(t * .pi)
        return content
            .scaleEffect(1 - t * 0.86)
            .rotationEffect(.degrees(-t * 400))
            .offset(x: t * target.width - bow * 18, y: t * target.height - bow * 26)
            .opacity(t > 0.86 ? (1 - t) / 0.14 : 1)
    }
}

/// Vilka månadsavsked som redan spelats.
///
/// Per BARN och månad, inte per enhet: en förälder som går in via "visa som barn" ska se
/// samma sekvens, och utan barnets id i nyckeln hade den ena telefonen firat om det den
/// andra redan gjort. Lokal med flit -- två föräldrar med varsin telefon får se den var
/// för sig, vilket är rätt sorts fel jämfört med att någon aldrig får se den alls.
///
/// UserDefaults och inte @AppStorage: nyckeln beror på barn och månad, och @AppStorage
/// vill ha en konstant.
enum FarewellLog {
    private static let key = "kq_farewells_seen"

    static func hasSeen(memberId: String, year: Int, month: Int) -> Bool {
        let seen = UserDefaults.standard.stringArray(forKey: key) ?? []
        return seen.contains(entry(memberId, year, month))
    }

    static func markSeen(memberId: String, year: Int, month: Int) {
        var seen = UserDefaults.standard.stringArray(forKey: key) ?? []
        seen.append(entry(memberId, year, month))
        // Bara de tolv senaste sparas. Listan växer annars för varje månad i en app tänkt
        // att användas i åratal, och ett avsked äldre än ett år kan aldrig bli aktuellt.
        UserDefaults.standard.set(Array(seen.suffix(12)), forKey: key)
    }

    private static func entry(_ memberId: String, _ year: Int, _ month: Int) -> String {
        "\(memberId):\(year)-\(month)"
    }
}
