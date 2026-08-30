import SwiftUI

/// The colours of the parent's view, which follow the season.
///
/// The season was already in the app -- every pet is drawn over a seasonal background --
/// but only inside a 72pt circle. Here it reaches the whole screen: the ground, the
/// cards' whiteness, the accent, the chips, the warning row and the band across the
/// top. Moving only the accent reads as a theme; moving everything reads as a
/// season, which is the point.
///
/// Deliberately NOT here: the species colours. Purple for a dragon and orange for a cat
/// are the child's own identity and must not drift with the calendar.
struct SeasonPalette: Sendable {
    let pageBg: Color
    let surface: Color
    let cardEdge: Color
    let ink: Color
    let inkSoft: Color
    let inkFaint: Color
    let accent: Color
    /// Text and icons drawn on top of `accent`.
    let onAccent: Color
    let outlineBg: Color
    let outlineEdge: Color
    let outlineInk: Color
    /// Unfilled part of a progress bar.
    let track: Color
    let goodBg: Color
    let goodInk: Color
    let calBg: Color
    let calInk: Color
    let warnBg: Color
    let warnInk: Color
    let warnStrong: Color
    let tipBg: Color
    let tipInk: Color
    let tipStrong: Color
    let badgeBg: Color
    let badgeInk: Color
    let badgeEdge: Color
    /// The header band, dark at both ends and the season's own colour in the middle.
    ///
    /// This was the seasonal photograph at first, at full size. It read as clutter:
    /// the artwork is detailed, and detail directly above a list of children competes
    /// with them. The photographs are still where they started, behind every pet
    /// portrait, at the size that suits them.
    let headerTop: Color
    let headerMid: Color
    let headerBottom: Color
    let danger: Color
    let dark: Bool
}

enum SeasonTheme {

    /// - Parameter season: one of the four names `currentSeason()` returns, so the
    ///   header band and the pet portraits can never disagree about which season it is.
    static func paletteFor(_ season: String, dark: Bool) -> SeasonPalette {
        if dark {
            return darkPalettes[season] ?? winterDark
        }
        return lightPalettes[season] ?? summerLight
    }

    /// Mar–May spring, Jun–Aug summer, Sep–Nov autumn, otherwise winter — the same
    /// four names, and the same cut-offs, as `PetImagesIOS.currentSeason()`.
    static func currentSeason() -> String {
        switch Calendar.current.component(.month, from: Date()) {
        case 3...5: return "spring"
        case 6...8: return "summer"
        case 9...11: return "autumn"
        default: return "winter"
        }
    }

    static func current(dark: Bool) -> SeasonPalette {
        paletteFor(currentSeason(), dark: dark)
    }

    // Spring is light and thin -- March, not July. Summer is the same green gone deep
    // and cool, which is what makes the change between them visible at all. Autumn is
    // the only season whose cards are warm rather than white. Winter is quiet on
    // purpose: lower contrast than the other three.
    private static let springLight = SeasonPalette(
        pageBg: Color(hex: 0xFFEEF4E6), surface: Color(hex: 0xFFFFFFFF), cardEdge: Color(hex: 0xFFDEE8D2),
        ink: Color(hex: 0xFF1B211A), inkSoft: Color(hex: 0xFF54604E), inkFaint: Color(hex: 0xFF88947D),
        accent: Color(hex: 0xFF3E7D3A), onAccent: Color(hex: 0xFFFFFFFF),
        // outlineBg is the page's own tint rather than the card's white: a quiet
        // button sits ON a card, and filling it with the card's colour made it the
        // same colour as the thing behind it -- a plate with a hairline round it and
        // nothing else to say it is pressable. Dark mode never had the problem, which
        // is why nobody running dark ever saw it.
        outlineBg: Color(hex: 0xFFEEF4E6), outlineEdge: Color(hex: 0xFFDEE8D2), outlineInk: Color(hex: 0xFF44403C),
        track: Color(hex: 0xFFE0EAD4),
        goodBg: Color(hex: 0xFFDCEFD5), goodInk: Color(hex: 0xFF2C5C2A),
        calBg: Color(hex: 0xFFEBF1DD), calInk: Color(hex: 0xFF5A6B2E),
        warnBg: Color(hex: 0xFFFBF2DA), warnInk: Color(hex: 0xFF786226), warnStrong: Color(hex: 0xFFA88318),
        tipBg: Color(hex: 0xFFF4F0DD), tipInk: Color(hex: 0xFF54563E), tipStrong: Color(hex: 0xFFA88318),
        badgeBg: Color(hex: 0xFFFFFFFF), badgeInk: Color(hex: 0xFF1B211A), badgeEdge: Color(hex: 0xFFEEF4E6),
        headerTop: Color(hex: 0xFF2F4526), headerMid: Color(hex: 0xFF8FB073), headerBottom: Color(hex: 0xFF24331C),
        danger: Color(hex: 0xFFC53030), dark: false
    )

    private static let summerLight = SeasonPalette(
        pageBg: Color(hex: 0xFFE7F1EE), surface: Color(hex: 0xFFFFFFFF), cardEdge: Color(hex: 0xFFD2E2DD),
        ink: Color(hex: 0xFF14201C), inkSoft: Color(hex: 0xFF4B5B55), inkFaint: Color(hex: 0xFF83918B),
        accent: Color(hex: 0xFF0F6A59), onAccent: Color(hex: 0xFFFFFFFF),
        // outlineBg is the page's own tint rather than the card's white: a quiet
        // button sits ON a card, and filling it with the card's colour made it the
        // same colour as the thing behind it -- a plate with a hairline round it and
        // nothing else to say it is pressable. Dark mode never had the problem, which
        // is why nobody running dark ever saw it.
        outlineBg: Color(hex: 0xFFE7F1EE), outlineEdge: Color(hex: 0xFFD2E2DD), outlineInk: Color(hex: 0xFF44403C),
        track: Color(hex: 0xFFD8E7E2),
        goodBg: Color(hex: 0xFFD3EDE1), goodInk: Color(hex: 0xFF0F5F4C),
        calBg: Color(hex: 0xFFDEEDE8), calInk: Color(hex: 0xFF0F6A59),
        warnBg: Color(hex: 0xFFFAEDD6), warnInk: Color(hex: 0xFF775726), warnStrong: Color(hex: 0xFFB0741C),
        tipBg: Color(hex: 0xFFE4EEEA), tipInk: Color(hex: 0xFF48524D), tipStrong: Color(hex: 0xFFB0741C),
        badgeBg: Color(hex: 0xFFFFFFFF), badgeInk: Color(hex: 0xFF14201C), badgeEdge: Color(hex: 0xFFE7F1EE),
        headerTop: Color(hex: 0xFF17403A), headerMid: Color(hex: 0xFF4E9E8C), headerBottom: Color(hex: 0xFF123330),
        danger: Color(hex: 0xFFC53030), dark: false
    )

    private static let autumnLight = SeasonPalette(
        pageBg: Color(hex: 0xFFF7EDDF), surface: Color(hex: 0xFFFFFCF6), cardEdge: Color(hex: 0xFFE9D9C1),
        ink: Color(hex: 0xFF241A12), inkSoft: Color(hex: 0xFF5E5044), inkFaint: Color(hex: 0xFF93816E),
        accent: Color(hex: 0xFFA0451E), onAccent: Color(hex: 0xFFFFFFFF),
        // outlineBg is the page's own tint rather than the card's white: a quiet
        // button sits ON a card, and filling it with the card's colour made it the
        // same colour as the thing behind it -- a plate with a hairline round it and
        // nothing else to say it is pressable. Dark mode never had the problem, which
        // is why nobody running dark ever saw it.
        outlineBg: Color(hex: 0xFFF7EDDF), outlineEdge: Color(hex: 0xFFE9D9C1), outlineInk: Color(hex: 0xFF4A3C30),
        track: Color(hex: 0xFFEBDCC6),
        goodBg: Color(hex: 0xFFE5EBD4), goodInk: Color(hex: 0xFF4A5B26),
        calBg: Color(hex: 0xFFF4E1CB), calInk: Color(hex: 0xFF8A4A1E),
        warnBg: Color(hex: 0xFFF6E3C9), warnInk: Color(hex: 0xFF785023), warnStrong: Color(hex: 0xFFA0451E),
        tipBg: Color(hex: 0xFFF1E4CF), tipInk: Color(hex: 0xFF584A3A), tipStrong: Color(hex: 0xFFA0451E),
        badgeBg: Color(hex: 0xFFFFFCF6), badgeInk: Color(hex: 0xFF241A12), badgeEdge: Color(hex: 0xFFF7EDDF),
        headerTop: Color(hex: 0xFF4A2A1A), headerMid: Color(hex: 0xFFC08760), headerBottom: Color(hex: 0xFF2C1A11),
        danger: Color(hex: 0xFFC53030), dark: false
    )

    private static let winterLight = SeasonPalette(
        pageBg: Color(hex: 0xFFE9EFF6), surface: Color(hex: 0xFFFBFCFE), cardEdge: Color(hex: 0xFFD7E0EA),
        ink: Color(hex: 0xFF141A21), inkSoft: Color(hex: 0xFF4D5765), inkFaint: Color(hex: 0xFF85909F),
        accent: Color(hex: 0xFF1D5C8A), onAccent: Color(hex: 0xFFFFFFFF),
        // outlineBg is the page's own tint rather than the card's white: a quiet
        // button sits ON a card, and filling it with the card's colour made it the
        // same colour as the thing behind it -- a plate with a hairline round it and
        // nothing else to say it is pressable. Dark mode never had the problem, which
        // is why nobody running dark ever saw it.
        outlineBg: Color(hex: 0xFFE9EFF6), outlineEdge: Color(hex: 0xFFD7E0EA), outlineInk: Color(hex: 0xFF44403C),
        track: Color(hex: 0xFFDCE4EE),
        goodBg: Color(hex: 0xFFD7EAE2), goodInk: Color(hex: 0xFF1F5F4A),
        calBg: Color(hex: 0xFFDEE9F3), calInk: Color(hex: 0xFF1D5C8A),
        warnBg: Color(hex: 0xFFF1E7D7), warnInk: Color(hex: 0xFF6C5939), warnStrong: Color(hex: 0xFF8A6A2E),
        tipBg: Color(hex: 0xFFE3E9F2), tipInk: Color(hex: 0xFF495261), tipStrong: Color(hex: 0xFF1D5C8A),
        badgeBg: Color(hex: 0xFFFBFCFE), badgeInk: Color(hex: 0xFF141A21), badgeEdge: Color(hex: 0xFFE9EFF6),
        headerTop: Color(hex: 0xFF1E2E3E), headerMid: Color(hex: 0xFF8FA5BC), headerBottom: Color(hex: 0xFF1A2430),
        danger: Color(hex: 0xFFC53030), dark: false
    )

    // Dark is a second value per colour, not a second design -- so the season still
    // shows through. Each ground carries its season's hue rather than all four
    // collapsing into the same charcoal.
    private static let springDark = darkPalette(
        pageBg: 0xFF0F160F, surface: 0xFF18211A, cardEdge: 0xFF26301F,
        accent: 0xFF8FD08A, calBg: 0xFF1B2A18, calInk: 0xFFB7D9A8,
        headerTop: 0xFF16200F, headerMid: 0xFF3E5237, headerBottom: 0xFF0F160F
    )

    private static let summerDark = darkPalette(
        pageBg: 0xFF0B1614, surface: 0xFF142020, cardEdge: 0xFF203030,
        accent: 0xFF62C9B0, calBg: 0xFF12292A, calInk: 0xFF8FD9C9,
        headerTop: 0xFF0E1F1C, headerMid: 0xFF2B5850, headerBottom: 0xFF0B1614
    )

    private static let autumnDark = darkPalette(
        pageBg: 0xFF17110C, surface: 0xFF221913, cardEdge: 0xFF35271D,
        accent: 0xFFE08A5A, calBg: 0xFF2E1F14, calInk: 0xFFE8B58C,
        headerTop: 0xFF1C140E, headerMid: 0xFF513528, headerBottom: 0xFF17110C
    )

    private static let winterDark = darkPalette(
        pageBg: 0xFF101620, surface: 0xFF19202B, cardEdge: 0xFF27303E,
        accent: 0xFF7FBBE3, calBg: 0xFF14283A, calInk: 0xFF8CC4E8,
        headerTop: 0xFF141C28, headerMid: 0xFF3A4B60, headerBottom: 0xFF101620
    )

    private static let lightPalettes: [String: SeasonPalette] = [
        "spring": springLight,
        "summer": summerLight,
        "autumn": autumnLight,
        "winter": winterLight,
    ]

    private static let darkPalettes: [String: SeasonPalette] = [
        "spring": springDark,
        "summer": summerDark,
        "autumn": autumnDark,
        "winter": winterDark,
    ]

    /// The parts of a dark palette that do not vary by season: text, warnings and the
    /// green that means done. Only the ground, the accent and the header carry the season.
    private static func darkPalette(
        pageBg: UInt32, surface: UInt32, cardEdge: UInt32,
        accent: UInt32, calBg: UInt32, calInk: UInt32,
        headerTop: UInt32, headerMid: UInt32, headerBottom: UInt32
    ) -> SeasonPalette {
        SeasonPalette(
            pageBg: Color(hex: pageBg), surface: Color(hex: surface), cardEdge: Color(hex: cardEdge),
            ink: Color(hex: 0xFFEEF1F5), inkSoft: Color(hex: 0xFFA2ACBA), inkFaint: Color(hex: 0xFF747F8E),
            accent: Color(hex: accent),
            // Every dark accent here is a light tint, so black text on it is what reads.
            onAccent: Color(hex: 0xFF0B1219),
            outlineBg: Color(hex: 0x0DFFFFFF), outlineEdge: Color(hex: cardEdge), outlineInk: Color(hex: 0xFFC3CBD6),
            track: Color(hex: cardEdge),
            goodBg: Color(hex: 0xFF123029), goodInk: Color(hex: 0xFF6FD3B4),
            calBg: Color(hex: calBg), calInk: Color(hex: calInk),
            warnBg: Color(hex: 0xFF2A2418), warnInk: Color(hex: 0xFFD5BB8B), warnStrong: Color(hex: 0xFFEFC97E),
            tipBg: Color(hex: 0xFF1D2531), tipInk: Color(hex: 0xFFB9C2CE), tipStrong: Color(hex: accent),
            badgeBg: Color(hex: surface), badgeInk: Color(hex: 0xFFEEF1F5), badgeEdge: Color(hex: pageBg),
            // Quieter than in light: the band should settle into a dark page rather than
            // glow out of it, which is what someone reading in bed needs.
            headerTop: Color(hex: headerTop), headerMid: Color(hex: headerMid), headerBottom: Color(hex: headerBottom),
            danger: Color(hex: 0xFFF08A8A), dark: true
        )
    }
}

private struct SeasonPaletteKey: EnvironmentKey {
    static let defaultValue = SeasonTheme.paletteFor("summer", dark: false)
}

extension EnvironmentValues {
    /// Read from the environment rather than passed down by hand: the dashboard is a
    /// dozen views deep and every one of them needs a colour or two.
    var seasonPalette: SeasonPalette {
        get { self[SeasonPaletteKey.self] }
        set { self[SeasonPaletteKey.self] = newValue }
    }
}

extension Color {
    /// ARGB with the alpha first, so the values can be transcribed unchanged from the
    /// Android palettes -- 0xFF1B211A there is 0xFF1B211A here.
    init(hex: UInt32) {
        let alpha = Double((hex >> 24) & 0xFF) / 255
        let red = Double((hex >> 16) & 0xFF) / 255
        let green = Double((hex >> 8) & 0xFF) / 255
        let blue = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}
