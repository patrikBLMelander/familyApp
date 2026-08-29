package se.kidquest.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import se.kidquest.app.pet.PetImages

/**
 * The colours of the parent's view, which follow the season.
 *
 * The season was already in the app -- every pet is drawn over a seasonal background --
 * but only inside a 72dp circle. Here it reaches the whole screen: the ground, the
 * cards' whiteness, the accent, the chips, the warning row and the band across the
 * top. Moving only the accent reads as a theme; moving everything reads as a
 * season, which is the point.
 *
 * Deliberately NOT here: the species colours. Purple for a dragon and orange for a cat
 * are the child's own identity and must not drift with the calendar -- [se.kidquest.app.pet.PetTheme]
 * still owns those.
 */
@Immutable
data class SeasonPalette(
    val pageBg: Color,
    val surface: Color,
    val cardEdge: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val accent: Color,
    /** Text and icons drawn on top of [accent]. */
    val onAccent: Color,
    val outlineBg: Color,
    val outlineEdge: Color,
    val outlineInk: Color,
    /** Unfilled part of a progress bar. */
    val track: Color,
    val goodBg: Color,
    val goodInk: Color,
    val calBg: Color,
    val calInk: Color,
    val warnBg: Color,
    val warnInk: Color,
    val warnStrong: Color,
    val tipBg: Color,
    val tipInk: Color,
    val tipStrong: Color,
    val badgeBg: Color,
    val badgeInk: Color,
    val badgeEdge: Color,
    /**
     * The header band, dark at both ends and the season's own colour in the middle.
     *
     * This was the seasonal photograph at first, at full size. It read as clutter:
     * the artwork is detailed, and detail directly above a list of children competes
     * with them. The photographs are still where they started, behind every pet
     * portrait, at the size that suits them.
     */
    val headerTop: Color,
    val headerMid: Color,
    val headerBottom: Color,
    val danger: Color,
    val dark: Boolean,
)

object SeasonTheme {

    /**
     * @param season one of the four names [PetImages.currentSeason] returns, so the
     *   header band and the pet portraits can never disagree about which season it is.
     */
    fun paletteFor(season: String, dark: Boolean): SeasonPalette =
        (if (dark) darkPalettes else lightPalettes)[season] ?: run {
            if (dark) darkPalettes.getValue("winter") else lightPalettes.getValue("summer")
        }

    fun current(dark: Boolean): SeasonPalette = paletteFor(PetImages.currentSeason(), dark)

    // Spring is light and thin -- March, not July. Summer is the same green gone deep
    // and cool, which is what makes the change between them visible at all. Autumn is
    // the only season whose cards are warm rather than white. Winter is quiet on
    // purpose: lower contrast than the other three.
    private val lightPalettes = mapOf(
        "spring" to SeasonPalette(
            pageBg = Color(0xFFEEF4E6), surface = Color(0xFFFFFFFF), cardEdge = Color(0xFFDEE8D2),
            ink = Color(0xFF1B211A), inkSoft = Color(0xFF54604E), inkFaint = Color(0xFF88947D),
            accent = Color(0xFF3E7D3A), onAccent = Color(0xFFFFFFFF),
            outlineBg = Color(0xFFFFFFFF), outlineEdge = Color(0xFFDEE8D2), outlineInk = Color(0xFF44403C),
            track = Color(0xFFE0EAD4),
            goodBg = Color(0xFFDCEFD5), goodInk = Color(0xFF2C5C2A),
            calBg = Color(0xFFEBF1DD), calInk = Color(0xFF5A6B2E),
            warnBg = Color(0xFFFBF2DA), warnInk = Color(0xFF786226), warnStrong = Color(0xFFA88318),
            tipBg = Color(0xFFF4F0DD), tipInk = Color(0xFF54563E), tipStrong = Color(0xFFA88318),
            badgeBg = Color(0xFFFFFFFF), badgeInk = Color(0xFF1B211A), badgeEdge = Color(0xFFEEF4E6),
            headerTop = Color(0xFF2F4526), headerMid = Color(0xFF8FB073), headerBottom = Color(0xFF24331C),
            danger = Color(0xFFC53030), dark = false,
        ),
        "summer" to SeasonPalette(
            pageBg = Color(0xFFE7F1EE), surface = Color(0xFFFFFFFF), cardEdge = Color(0xFFD2E2DD),
            ink = Color(0xFF14201C), inkSoft = Color(0xFF4B5B55), inkFaint = Color(0xFF83918B),
            accent = Color(0xFF0F6A59), onAccent = Color(0xFFFFFFFF),
            outlineBg = Color(0xFFFFFFFF), outlineEdge = Color(0xFFD2E2DD), outlineInk = Color(0xFF44403C),
            track = Color(0xFFD8E7E2),
            goodBg = Color(0xFFD3EDE1), goodInk = Color(0xFF0F5F4C),
            calBg = Color(0xFFDEEDE8), calInk = Color(0xFF0F6A59),
            warnBg = Color(0xFFFAEDD6), warnInk = Color(0xFF775726), warnStrong = Color(0xFFB0741C),
            tipBg = Color(0xFFE4EEEA), tipInk = Color(0xFF48524D), tipStrong = Color(0xFFB0741C),
            badgeBg = Color(0xFFFFFFFF), badgeInk = Color(0xFF14201C), badgeEdge = Color(0xFFE7F1EE),
            headerTop = Color(0xFF17403A), headerMid = Color(0xFF4E9E8C), headerBottom = Color(0xFF123330),
            danger = Color(0xFFC53030), dark = false,
        ),
        "autumn" to SeasonPalette(
            pageBg = Color(0xFFF7EDDF), surface = Color(0xFFFFFCF6), cardEdge = Color(0xFFE9D9C1),
            ink = Color(0xFF241A12), inkSoft = Color(0xFF5E5044), inkFaint = Color(0xFF93816E),
            accent = Color(0xFFA0451E), onAccent = Color(0xFFFFFFFF),
            outlineBg = Color(0xFFFFFCF6), outlineEdge = Color(0xFFE9D9C1), outlineInk = Color(0xFF4A3C30),
            track = Color(0xFFEBDCC6),
            goodBg = Color(0xFFE5EBD4), goodInk = Color(0xFF4A5B26),
            calBg = Color(0xFFF4E1CB), calInk = Color(0xFF8A4A1E),
            warnBg = Color(0xFFF6E3C9), warnInk = Color(0xFF785023), warnStrong = Color(0xFFA0451E),
            tipBg = Color(0xFFF1E4CF), tipInk = Color(0xFF584A3A), tipStrong = Color(0xFFA0451E),
            badgeBg = Color(0xFFFFFCF6), badgeInk = Color(0xFF241A12), badgeEdge = Color(0xFFF7EDDF),
            headerTop = Color(0xFF4A2A1A), headerMid = Color(0xFFC08760), headerBottom = Color(0xFF2C1A11),
            danger = Color(0xFFC53030), dark = false,
        ),
        "winter" to SeasonPalette(
            pageBg = Color(0xFFE9EFF6), surface = Color(0xFFFBFCFE), cardEdge = Color(0xFFD7E0EA),
            ink = Color(0xFF141A21), inkSoft = Color(0xFF4D5765), inkFaint = Color(0xFF85909F),
            accent = Color(0xFF1D5C8A), onAccent = Color(0xFFFFFFFF),
            outlineBg = Color(0xFFFBFCFE), outlineEdge = Color(0xFFD7E0EA), outlineInk = Color(0xFF44403C),
            track = Color(0xFFDCE4EE),
            goodBg = Color(0xFFD7EAE2), goodInk = Color(0xFF1F5F4A),
            calBg = Color(0xFFDEE9F3), calInk = Color(0xFF1D5C8A),
            warnBg = Color(0xFFF1E7D7), warnInk = Color(0xFF6C5939), warnStrong = Color(0xFF8A6A2E),
            tipBg = Color(0xFFE3E9F2), tipInk = Color(0xFF495261), tipStrong = Color(0xFF1D5C8A),
            badgeBg = Color(0xFFFBFCFE), badgeInk = Color(0xFF141A21), badgeEdge = Color(0xFFE9EFF6),
            headerTop = Color(0xFF1E2E3E), headerMid = Color(0xFF8FA5BC), headerBottom = Color(0xFF1A2430),
            danger = Color(0xFFC53030), dark = false,
        ),
    )

    // Dark is a second value per colour, not a second design -- so the season still
    // shows through. Each ground carries its season's hue rather than all four
    // collapsing into the same charcoal.
    private val darkPalettes = mapOf(
        "spring" to darkPalette(
            pageBg = 0xFF0F160F, surface = 0xFF18211A, cardEdge = 0xFF26301F,
            accent = 0xFF8FD08A, calBg = 0xFF1B2A18, calInk = 0xFFB7D9A8,
            headerTop = 0xFF16200F, headerMid = 0xFF3E5237, headerBottom = 0xFF0F160F,
        ),
        "summer" to darkPalette(
            pageBg = 0xFF0B1614, surface = 0xFF142020, cardEdge = 0xFF203030,
            accent = 0xFF62C9B0, calBg = 0xFF12292A, calInk = 0xFF8FD9C9,
            headerTop = 0xFF0E1F1C, headerMid = 0xFF2B5850, headerBottom = 0xFF0B1614,
        ),
        "autumn" to darkPalette(
            pageBg = 0xFF17110C, surface = 0xFF221913, cardEdge = 0xFF35271D,
            accent = 0xFFE08A5A, calBg = 0xFF2E1F14, calInk = 0xFFE8B58C,
            headerTop = 0xFF1C140E, headerMid = 0xFF513528, headerBottom = 0xFF17110C,
        ),
        "winter" to darkPalette(
            pageBg = 0xFF101620, surface = 0xFF19202B, cardEdge = 0xFF27303E,
            accent = 0xFF7FBBE3, calBg = 0xFF14283A, calInk = 0xFF8CC4E8,
            headerTop = 0xFF141C28, headerMid = 0xFF3A4B60, headerBottom = 0xFF101620,
        ),
    )

    /**
     * The parts of a dark palette that do not vary by season: text, warnings and the
     * green that means done. Only the ground, the accent and the header carry the season.
     */
    private fun darkPalette(
        pageBg: Long, surface: Long, cardEdge: Long,
        accent: Long, calBg: Long, calInk: Long,
        headerTop: Long, headerMid: Long, headerBottom: Long,
    ) = SeasonPalette(
        pageBg = Color(pageBg), surface = Color(surface), cardEdge = Color(cardEdge),
        ink = Color(0xFFEEF1F5), inkSoft = Color(0xFFA2ACBA), inkFaint = Color(0xFF747F8E),
        accent = Color(accent),
        // Every dark accent here is a light tint, so black text on it is what reads.
        onAccent = Color(0xFF0B1219),
        outlineBg = Color(0x0DFFFFFF), outlineEdge = Color(cardEdge), outlineInk = Color(0xFFC3CBD6),
        track = Color(cardEdge),
        goodBg = Color(0xFF123029), goodInk = Color(0xFF6FD3B4),
        calBg = Color(calBg), calInk = Color(calInk),
        warnBg = Color(0xFF2A2418), warnInk = Color(0xFFD5BB8B), warnStrong = Color(0xFFEFC97E),
        tipBg = Color(0xFF1D2531), tipInk = Color(0xFFB9C2CE), tipStrong = Color(accent),
        badgeBg = Color(surface), badgeInk = Color(0xFFEEF1F5), badgeEdge = Color(pageBg),
        // Quieter than in light: the band should settle into a dark page rather than
        // glow out of it, which is what someone reading in bed needs.
        headerTop = Color(headerTop), headerMid = Color(headerMid), headerBottom = Color(headerBottom),
        danger = Color(0xFFF08A8A), dark = true,
    )
}

/**
 * Read anywhere under [se.kidquest.app.ui.theme.KidQuestTheme] rather than passed down
 * by hand: the dashboard is a dozen composables deep and every one of them needs a
 * colour or two.
 */
val LocalSeasonPalette = staticCompositionLocalOf {
    SeasonTheme.paletteFor("summer", dark = false)
}
