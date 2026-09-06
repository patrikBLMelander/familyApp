import SwiftUI

/// Vad kodrutan används till just nu.
enum PinPurpose {
    /// Föräldern sätter en kod för första gången.
    case set
    /// Någon vill lämna barnläget och måste skriva koden.
    case unlock
    /// Föräldern ändrar eller tar bort en kod som redan finns.
    case change
}

/// Kodrutan för att lämna barnläget.
///
/// Koden skyddar mot ett barn som trycker "Tillbaka" av nyfikenhet, inte mot en angripare.
/// Det avgör ambitionsnivån: fyra siffror, en paus efter fem fel, och en väg ut som är
/// utloggning i stället för en lösenordskontroll.
///
/// Utloggning som reservväg är inte en genväg för barnet. Den som loggar ut hamnar på
/// inloggningsskärmen, inte i föräldravyn -- och för att komma tillbaka in behövs
/// lösenordet, som barnet inte har. Alternativet hade varit att verifiera lösenordet här,
/// men sessionen sparar ingen e-postadress, så det hade betytt att skriva både adress och
/// lösenord på en skärm vars hela poäng är fyra siffror.
///
/// Android har samma vy i `ParentPinDialog.kt`, med samma regler.
struct ParentPinSheet: View {
    let purpose: PinPurpose
    let palette: SeasonPalette
    var childName: String?
    var verify: (String) -> Bool = { _ in false }
    var onPinChosen: (String?) -> Void = { _ in }
    var onUnlocked: () -> Void = {}
    var onSignOut: () -> Void = {}
    var onDismiss: () -> Void = {}

    private static let maxAttempts = 5
    private static let lockoutSeconds = 30

    @State private var entered = ""
    /// Vid set och change skrivs koden två gånger; det här är den första.
    @State private var firstEntry: String?
    @State private var error: String?
    @State private var attempts = 0
    @State private var lockedFor = 0

    private var settingNew: Bool { purpose != .unlock }
    private var confirming: Bool { firstEntry != nil }

    var body: some View {
        VStack(spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundStyle(palette.ink)

            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(palette.inkSoft)
                .multilineTextAlignment(.center)

            Text(error ?? " ")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.danger)
                .frame(minHeight: 18)

            HStack(spacing: 14) {
                ForEach(0..<4, id: \.self) { i in
                    Circle()
                        .fill(i < entered.count ? palette.accent : .clear)
                        .overlay(
                            Circle().stroke(
                                error != nil ? palette.danger
                                    : (i < entered.count ? palette.accent : palette.cardEdge),
                                lineWidth: 1.8)
                        )
                        .frame(width: 14, height: 14)
                }
            }

            keypad

            HStack {
                Button("Avbryt", action: onDismiss)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.inkSoft)
                Spacer()
                if purpose == .change {
                    Button("Ta bort koden") { onPinChosen(nil) }
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.danger)
                } else if purpose == .unlock {
                    Button("Glömt? Logga ut", action: onSignOut)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.inkSoft)
                }
            }
            .padding(.top, 4)
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous).fill(palette.surface)
        )
        .padding(.horizontal, 24)
        .task(id: lockedFor) {
            guard lockedFor > 0 else { return }
            try? await Task.sleep(for: .seconds(1))
            lockedFor -= 1
        }
    }

    private var title: String {
        if confirming { return "En gång till" }
        switch purpose {
        case .unlock: return "Skriv koden"
        case .change: return "Välj en ny kod"
        case .set: return "Välj en kod"
        }
    }

    private var subtitle: String {
        if lockedFor > 0 { return "Vänta \(lockedFor) sekunder." }
        if confirming { return "Så att den inte blev fel." }
        if purpose == .unlock {
            if let childName { return "För att lämna \(possessiveSwedish(childName)) vy." }
            return "För att lämna barnläget."
        }
        return "Fyra siffror. Den behövs för att komma tillbaka hit."
    }

    /// Nollan i mitten på nedersta raden, som på en telefon.
    private var keypad: some View {
        VStack(spacing: 8) {
            ForEach([["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["", "0", "⌫"]], id: \.self) { row in
                HStack(spacing: 8) {
                    ForEach(row, id: \.self) { label in
                        if label.isEmpty {
                            Color.clear.frame(width: 76, height: 52)
                        } else {
                            Button { tap(label) } label: {
                                Text(label)
                                    .font(.title3.weight(.semibold))
                                    .foregroundStyle(lockedFor == 0 ? palette.ink : palette.inkFaint)
                                    .frame(width: 76, height: 52)
                                    .background(
                                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                                            .fill(palette.outlineBg)
                                    )
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 13, style: .continuous)
                                            .stroke(palette.cardEdge, lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                            .disabled(lockedFor > 0)
                        }
                    }
                }
            }
        }
    }

    private func tap(_ label: String) {
        if label == "⌫" { entered = String(entered.dropLast()); return }
        guard entered.count < 4 else { return }
        error = nil
        entered += label
        if entered.count == 4 { submit() }
    }

    private func submit() {
        let code = entered
        entered = ""
        if settingNew {
            guard let first = firstEntry else { firstEntry = code; error = nil; return }
            if first != code {
                firstEntry = nil
                error = "Koderna var olika. Försök igen."
            } else {
                onPinChosen(code)
            }
            return
        }
        if verify(code) { onUnlocked(); return }
        attempts += 1
        if attempts >= Self.maxAttempts {
            attempts = 0
            lockedFor = Self.lockoutSeconds
            error = "För många försök."
        } else {
            error = "Fel kod."
        }
    }
}

