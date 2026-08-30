//
//  InviteQRScannerView.swift
//  KidQuest
//
//  Arket som barnet ser när det trycker "Skanna QR-kod".
//

import AVFoundation
import SwiftUI

/// Skannar en familjs inbjudnings-QR och lämnar tillbaka koden.
///
/// Presented as a sheet from ChildInviteLoginView. It produces a token string and
/// nothing else — the caller feeds it to the same `performLink` a typed code goes
/// through, so scanning and typing cannot drift apart.
///
/// The camera is the most invasive permission this app asks for, and the person being
/// asked is a child. So the ask happens here, on this sheet, which only exists because
/// they tapped scan — never at launch, and never on a screen they merely walked past.
struct InviteQRScannerView: View {

    /// Anropas med koden ur QR:en, precis innan arket stänger sig.
    var onScanned: (String) -> Void = { _ in }

    /// Tvingar fram ett läge åt debug-riggen. Bara `fixture()` sätter den.
    ///
    /// Same idea as ChildInviteLoginView.prefill: the simulator has no camera, so the
    /// only state it can reach on its own is "ingen kamera". Everything else needs a
    /// way in that does not involve hardware.
    var forcedPhase: InviteQRScannerModel.Phase?

    @Environment(\.seasonPalette) private var palette
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var model = InviteQRScannerModel()

    private var phase: InviteQRScannerModel.Phase {
        forcedPhase ?? model.phase
    }

    var body: some View {
        VStack(spacing: 0) {
            SeasonHeaderBar(
                title: "Skanna QR-kod",
                subtitle: "Rikta kameran mot koden på förälderns skärm",
                onBack: { dismiss() }
            )

            Group {
                switch phase {
                case .preparing:
                    preparing
                case .scanning:
                    camera
                case .blocked(let block):
                    blocked(block)
                case .unreadable:
                    unreadable
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(palette.pageBg.ignoresSafeArea())
        .task {
            // Guarded so the debug fixtures never trip the permission prompt: a
            // forced phase is a picture of a state, not a run through it.
            guard forcedPhase == nil else { return }
            await model.prepare()
        }
        .onDisappear {
            model.stop()
        }
        .onChange(of: model.scannedToken) { _, token in
            guard let token else { return }
            // Hand the code up first, then close. The caller sets its own loading
            // state on the screen underneath, so it is already showing "Kopplar…"
            // by the time the sheet has finished sliding away.
            onScanned(token)
            dismiss()
        }
    }

    // MARK: - Lägen

    /// Ett ögonblick medan enhet och tillstånd kollas. Sällan synligt, men aldrig tomt.
    private var preparing: some View {
        VStack(spacing: 14) {
            ProgressView()
                .tint(palette.accent)
            Text("Startar kameran…")
                .font(.system(size: 14))
                .foregroundStyle(palette.inkSoft)
        }
    }

    private var camera: some View {
        VStack(spacing: 0) {
            ZStack {
                InviteQRCameraPreview(session: model.session)
                    // A new session after a restart is a new preview; without this the
                    // old, already-spent one would be left on screen showing nothing.
                    .id(model.sessionGeneration)
                    .ignoresSafeArea(edges: .bottom)

                ScanReticle()
            }

            Text("Håll koden inom rutan.")
                .font(.system(size: 14))
                .foregroundStyle(palette.inkSoft)
                .padding(.vertical, 18)
        }
    }

    @ViewBuilder
    private func blocked(_ block: InviteScannerBlock) -> some View {
        switch block {
        case .noCamera:
            Notice(
                symbol: "camera.badge.ellipsis",
                title: "Ingen kamera på den här enheten",
                message: "Den här enheten har ingen kamera att skanna med. Skriv in koden för hand i stället — den står under QR-koden på förälderns skärm.",
                primary: .init(title: "Skriv in koden") { dismiss() }
            )
        case .permissionDenied:
            Notice(
                symbol: "camera.fill",
                title: "KidQuest får inte använda kameran",
                // Nothing in the app can grant this — the system will not ask twice —
                // so the only honest next step is the door out to Settings.
                message: "Kameran är avstängd för KidQuest. Du kan slå på den i Inställningar, eller skriva in koden för hand i stället.",
                primary: .init(title: "Öppna Inställningar") { openSettings() },
                secondary: .init(title: "Skriv in koden") { dismiss() }
            )
        case .permissionRestricted:
            Notice(
                symbol: "lock.fill",
                title: "Kameran är låst på den här enheten",
                // Screen Time or a school MDM profile. A child tapping through to
                // Settings would find the switch greyed out, so do not send them there.
                message: "Kameran är avstängd av Skärmtid eller av enhetens inställningar, och kan bara låsas upp av den som satte begränsningen. Skriv in koden för hand så länge.",
                primary: .init(title: "Skriv in koden") { dismiss() }
            )
        case .sessionFailed:
            Notice(
                symbol: "exclamationmark.triangle.fill",
                title: "Kameran gick inte att starta",
                message: "Något annat använder kanske kameran just nu. Försök igen, eller skriv in koden för hand.",
                primary: .init(title: "Försök igen") { model.restart() },
                secondary: .init(title: "Skriv in koden") { dismiss() }
            )
        }
    }

    private var unreadable: some View {
        Notice(
            symbol: "questionmark.square.dashed",
            title: "Det var ingen inbjudningskod",
            message: "QR-koden gick att läsa, men den innehöll ingen kod till en familj. Kolla att det är rätt kod på skärmen och försök igen.",
            primary: .init(title: "Skanna igen") { model.restart() },
            secondary: .init(title: "Skriv in koden") { dismiss() }
        )
    }

    private func openSettings() {
        // Deep-links to KidQuest's own page in Settings, where the camera switch is.
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        openURL(url)
    }
}

// MARK: - Delar

/// Ramen som visar var koden ska hamna.
///
/// Only corner marks, not a full rectangle: a closed frame reads as something the code
/// must fit exactly, and children hold the phone too close and then give up.
private struct ScanReticle: View {

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height) * 0.68

            ZStack {
                // Everything outside the window is dimmed, so the eye lands in it.
                Color.black.opacity(0.42)
                    .mask {
                        Rectangle()
                            .overlay {
                                RoundedRectangle(cornerRadius: 22, style: .continuous)
                                    .frame(width: side, height: side)
                                    .blendMode(.destinationOut)
                            }
                            .compositingGroup()
                    }

                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.9), lineWidth: 3)
                    .frame(width: side, height: side)
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .allowsHitTesting(false)
        }
    }
}

/// En hel skärm som förklarar varför det inte går, och vad barnet kan göra i stället.
///
/// Every blocked state gets one of these rather than a black rectangle: a child staring
/// at a dead camera has no way to tell a denied permission from a broken app.
private struct Notice: View {
    @Environment(\.seasonPalette) private var palette

    struct Action {
        let title: String
        let handler: () -> Void
    }

    let symbol: String
    let title: String
    let message: String
    var primary: Action?
    var secondary: Action?

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 12)

            VStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 40, weight: .regular))
                    .foregroundStyle(palette.accent)

                Text(title)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(palette.ink)
                    .multilineTextAlignment(.center)

                Text(message)
                    .font(.system(size: 14))
                    .foregroundStyle(palette.inkSoft)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 28)

            Spacer(minLength: 16)

            VStack(spacing: 10) {
                if let primary {
                    Button(action: primary.handler) {
                        Text(primary.title)
                            .font(.system(size: 16, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .foregroundStyle(palette.onAccent)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(palette.accent)
                            )
                    }
                    .buttonStyle(.plain)
                }

                if let secondary {
                    Button(action: secondary.handler) {
                        Text(secondary.title)
                            .font(.system(size: 15.5, weight: .semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .foregroundStyle(palette.accent)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .strokeBorder(palette.accent, lineWidth: 1.5)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Debug

#if DEBUG
extension InviteQRScannerView {

    /// Ett givet läge, utan kamera och utan systemfråga.
    ///
    /// The simulator has no camera at all, so `.blocked(.noCamera)` is the only state
    /// it reaches by itself — every other one needs this way in to be looked at.
    static func fixture(phase: InviteQRScannerModel.Phase) -> InviteQRScannerView {
        InviteQRScannerView(forcedPhase: phase)
    }
}

#Preview("Nekad kamera") {
    InviteQRScannerView.fixture(phase: .blocked(.permissionDenied))
        .environment(\.seasonPalette, SeasonTheme.paletteFor("summer", dark: false))
}

#Preview("Ingen kamera") {
    InviteQRScannerView.fixture(phase: .blocked(.noCamera))
        .environment(\.seasonPalette, SeasonTheme.paletteFor("autumn", dark: false))
}

#Preview("Låst av Skärmtid") {
    InviteQRScannerView.fixture(phase: .blocked(.permissionRestricted))
        .environment(\.seasonPalette, SeasonTheme.paletteFor("winter", dark: false))
}

#Preview("Kameran startade inte") {
    InviteQRScannerView.fixture(phase: .blocked(.sessionFailed))
        .environment(\.seasonPalette, SeasonTheme.paletteFor("spring", dark: false))
}

#Preview("Fel sorts QR") {
    InviteQRScannerView.fixture(phase: .unreadable)
        .environment(\.seasonPalette, SeasonTheme.paletteFor("summer", dark: true))
        .preferredColorScheme(.dark)
}

#Preview("Startar") {
    InviteQRScannerView.fixture(phase: .preparing)
        .environment(\.seasonPalette, SeasonTheme.paletteFor("summer", dark: false))
}
#endif
