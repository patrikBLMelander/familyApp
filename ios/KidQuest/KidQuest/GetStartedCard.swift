import SwiftUI

/// Kom igång-guiden på föräldrarnas översikt.
///
/// Fanns bara på Android, så en ny familj fick ingen vägledning alls på iOS -- de såg
/// en tom lista och en knapp, utan att veta att ingenting i appen fungerar förrän det
/// finns ett barn.
///
/// Inget av tillståndet sparas utom "dölj". De fyra bockarna räknas fram ur familjens
/// faktiska data varje gång, vilket är det som gör att guiden överlever en
/// ominstallation, är färdig direkt för en familj som registrerat sig på webben, och
/// korrekt kommer tillbaka om en förälder senare tar bort sitt enda barn.
struct GetStartedState {
    let hasChild: Bool
    let hasChores: Bool
    let hasPairedDevice: Bool
    let hasPet: Bool

    var doneCount: Int {
        [hasChild, hasChores, hasPairedDevice, hasPet].filter { $0 }.count
    }

    /// Koppling ligger sist med flit: det är steget som får hoppas över, så det ska
    /// aldrig vara det en förälder uppmanas göra härnäst.
    var nextLabel: String? {
        if !hasChild { return "lägg till ett barn" }
        if !hasChores { return "lägg till dagliga sysslor" }
        if !hasPet { return "välj ett ägg" }
        if !hasPairedDevice { return "koppla barnets telefon" }
        return nil
    }

    var isComplete: Bool { nextLabel == nil }
}

struct GetStartedCard: View {

    let state: GetStartedState
    var onAddChild: () -> Void
    var onAddChores: () -> Void
    var onPairDevice: () -> Void
    var onSeePet: () -> Void
    var onCollapse: () -> Void
    var onDismiss: () -> Void

    @Environment(\.seasonPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            header
            Spacer().frame(height: 4)
            row(
                done: state.hasChild,
                title: "Lägg till ett barn",
                subtitle: "Inget fungerar förrän det finns ett barn i familjen.",
                action: "Lägg till",
                enabled: true,
                onTap: onAddChild
            )
            row(
                done: state.hasChores,
                title: "Lägg till dagliga sysslor",
                subtitle: "Välj ålder när du lägger till barnet och du får förslag direkt.",
                action: "Lägg till",
                // Bara meningsfullt när det finns ett barn -- sysslorna hör till ett.
                enabled: state.hasChild,
                onTap: onAddChores
            )
            row(
                done: state.hasPet,
                title: "Välj ett ägg",
                subtitle: "Barnet får ett djur att ta hand om — det är hela poängen.",
                action: "Öppna",
                enabled: state.hasChild,
                onTap: onSeePet
            )
            row(
                done: state.hasPairedDevice,
                title: "Koppla barnets telefon",
                subtitle: "Hoppa över det här om barnet inte har någon egen telefon — "
                    + "du kan visa barnets vy från ditt eget konto.",
                action: "Visa kod",
                enabled: state.hasChild,
                onTap: onPairDevice,
                isLast: true
            )
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        // Kortytan och inte tipBg: i sommarpaletten skiljer tipBg och pageBg tre steg,
        // så kortet hade bara sin tunna kontur att stå på. Att den läses som ett tips
        // och inte som vilket kort som helst gör den guldfärgade rubriken i stället.
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(palette.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(palette.cardEdge, lineWidth: 1)
        )
    }

    private var header: some View {
        HStack(alignment: .center) {
            // Ett tryck på rubriken viker ihop guiden till en rad. "Dölj" bredvid är
            // permanent; det här är det inte.
            Button(action: onCollapse) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Kom igång")
                        .font(.headline)
                        .foregroundStyle(palette.tipStrong)
                    Text("\(state.doneCount) av 4 klara")
                        .font(.caption)
                        .foregroundStyle(palette.tipInk)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button("Dölj", action: onDismiss)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.accent)
        }
    }

    private func row(
        done: Bool,
        title: String,
        subtitle: String,
        action: String,
        enabled: Bool,
        onTap: @escaping () -> Void,
        isLast: Bool = false
    ) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(done ? palette.goodInk : Color.clear)
                    .overlay(
                        Circle().stroke(done ? palette.goodInk : palette.track, lineWidth: 2)
                    )
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Color.white)
                            .opacity(done ? 1 : 0)
                    )
                    .frame(width: 22, height: 22)
                    .padding(.top, 2)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.tipStrong)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(palette.tipInk)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if !done {
                    Button(action: onTap) {
                        Text(action)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(enabled ? palette.accent : palette.inkFaint)
                    }
                    .buttonStyle(.plain)
                    .disabled(!enabled)
                    .padding(.top, 2)
                }
            }
            .padding(.vertical, 8)

            if !isLast {
                Divider().overlay(palette.cardEdge.opacity(0.6))
            }
        }
    }
}

/// Guiden ihopvikt till en rad.
///
/// En familj som inte börjat ser hela guiden. Så snart något steg är klart viker den
/// ihop sig, så att fyra uppstartssteg slutar ta den skärmyta som hör till barnen.
struct GetStartedStrip: View {

    let state: GetStartedState
    var onExpand: () -> Void

    @Environment(\.seasonPalette) private var palette

    var body: some View {
        Button(action: onExpand) {
            HStack(spacing: 11) {
                HStack(spacing: 4) {
                    ForEach(0..<4, id: \.self) { index in
                        Circle()
                            .fill(index < state.doneCount ? palette.accent : palette.track)
                            .frame(width: 7, height: 7)
                    }
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text("Kom igång — \(state.doneCount) av 4 klara")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.tipStrong)
                    if let next = state.nextLabel {
                        Text("Nästa: \(next)")
                            .font(.caption2)
                            .foregroundStyle(palette.tipInk)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.inkFaint)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(palette.cardEdge, lineWidth: 1)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Visa alla steg")
    }
}
