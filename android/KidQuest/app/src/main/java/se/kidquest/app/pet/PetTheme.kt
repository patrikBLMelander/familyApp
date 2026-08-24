package se.kidquest.app.pet

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Per-species colour, mirroring frontend/src/features/pet/petTheme.ts.
 *
 * The same values were previously written twice as `when` blocks inside
 * ChildDashboardScreen, which is how shark and lion came to be missing from both:
 * they were added to the web palette and to the art, but not to the Android copy, so
 * a child with either species fell through to the neutral pastel default. Having one
 * table means adding a species touches one place.
 *
 * [Palette.accent] is the reason this file exists rather than just holding gradients.
 * Neither end of a species gradient is reliably usable on its own: dragon's pale end
 * is nearly black, unicorn's saturated end is a pastel pink. `accent` is the member
 * picked to stay visible as a 4dp stroke or a 1.5dp border on the cream cards, which
 * is what the parent dashboard draws progress rings with. It is deliberately NOT a
 * text colour — several accents sit near 2.5:1 against cream.
 */
object PetTheme {

    data class Palette(
        /** Pale end of the species gradient — the top of the child's own screen. */
        val from: Color,
        /** Saturated end. */
        val to: Color,
        /** Legible as a stroke or border on a cream card. Not for text. */
        val accent: Color,
        /** Light card fill in the species' family. */
        val cardTint: Color,
        /** One step deeper, for a card nested inside a card. */
        val cardTintInner: Color,
    )

    private val LIGHT_VIOLET = Color(0xFFEDE9FE) to Color(0xFFDDD6FE)
    private val LIGHT_AMBER = Color(0xFFFFFBEB) to Color(0xFFFEF3C7)
    private val LIGHT_GREEN = Color(0xFFDCFCE7) to Color(0xFFBBF7D0)
    private val LIGHT_BLUE = Color(0xFFEFF6FF) to Color(0xFFDBEAFE)
    private val LIGHT_PINK = Color(0xFFFDF2F8) to Color(0xFFFCE7F3)
    private val LIGHT_GREY = Color(0xFFF3F4F6) to Color(0xFFE5E7EB)

    private fun palette(
        from: Long,
        to: Long,
        accent: Long,
        tints: Pair<Color, Color>,
    ) = Palette(Color(from), Color(to), Color(accent), tints.first, tints.second)

    private val palettes: Map<String, Palette> = mapOf(
        "dragon" to palette(0xFF4C1D95, 0xFF1E293B, 0xFF4C1D95, LIGHT_VIOLET),
        "cat" to palette(0xFFFDE68A, 0xFFF97316, 0xFFF97316, LIGHT_AMBER),
        "dog" to palette(0xFFBBF7D0, 0xFF22C55E, 0xFF16A34A, LIGHT_GREEN),
        "bird" to palette(0xFFBFDBFE, 0xFF2563EB, 0xFF2563EB, LIGHT_BLUE),
        "rabbit" to palette(0xFFFCE7F3, 0xFFEC4899, 0xFFDB2777, LIGHT_PINK),
        "bear" to palette(0xFFFEF3C7, 0xFF92400E, 0xFF92400E, LIGHT_AMBER),
        "snake" to palette(0xFFDCFCE7, 0xFF15803D, 0xFF15803D, LIGHT_GREEN),
        "panda" to palette(0xFFE5E7EB, 0xFF111827, 0xFF111827, LIGHT_GREY),
        "slot" to palette(0xFFE5E7EB, 0xFF6B7280, 0xFF4B5563, LIGHT_GREY),
        "hydra" to palette(0xFFC4B5FD, 0xFF4C1D95, 0xFF4C1D95, LIGHT_VIOLET),
        // Both ends of unicorn are pastel, so the accent steps down the same hue
        // ramp to a strength that still reads as a ring.
        "unicorn" to palette(0xFFFDE68A, 0xFFF9A8D4, 0xFFDB2777, LIGHT_PINK),
        "kapybara" to palette(0xFFDCFCE7, 0xFF22C55E, 0xFF16A34A, LIGHT_GREEN),
        "shark" to palette(0xFFBAE6FD, 0xFF0369A1, 0xFF0369A1, LIGHT_BLUE),
        "lion" to palette(0xFFFEF3C7, 0xFFD97706, 0xFFD97706, LIGHT_AMBER),
    )

    /** For a child who has not chosen an egg yet: the app's own background pastels. */
    private val NEUTRAL = Palette(
        from = Color(0xFFE0E7FF),
        to = Color(0xFFE0F2FE),
        accent = Color(0xFF0C4A6E),
        cardTint = Color(0xFFFFFBEB),
        cardTintInner = Color(0xFFEFF6FF),
    )

    /** Accepts a petType or an eggType; the API hands out both. */
    fun forPet(petType: String?): Palette {
        val species = PetImages.petTypeForEgg(petType) ?: return NEUTRAL
        return palettes[species] ?: NEUTRAL
    }

    /** Full-bleed background for the child's own screen. */
    fun background(petType: String?): Brush = with(forPet(petType)) {
        Brush.verticalGradient(listOf(from, to))
    }

    /** The sliver along the top of a card, so it reads as a piece of that screen. */
    fun edge(petType: String?): Brush = with(forPet(petType)) {
        Brush.horizontalGradient(listOf(from, to))
    }
}
