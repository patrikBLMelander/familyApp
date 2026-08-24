package se.kidquest.app.pet

import android.content.Context
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves pet artwork by resource name rather than a hand-written R.drawable table.
 *
 * Adding a species means dropping `<species>_stage1..5.png` and
 * `<species>_egg_stage1..5.png` into res/drawable and adding one line to EGG_TO_PET.
 *
 * The integrated variant (pet with a background baked in) is gone. Pets are drawn as
 * transparent standalone art over a seasonal background, matching the web app since
 * d190a65 — see PetVisual.
 *
 * Because lookups are by name, res/raw/keep.xml lists these drawables so resource
 * shrinking cannot remove them once R8 is enabled.
 */
object PetImages {

    /** Mirrors EGG_TO_PET_MAP in the backend's PetService. */
    private val EGG_TO_PET = mapOf(
        "blue_egg" to "dragon",
        "green_egg" to "cat",
        "red_egg" to "dog",
        "yellow_egg" to "bird",
        "purple_egg" to "rabbit",
        "orange_egg" to "bear",
        "brown_egg" to "snake",
        "black_egg" to "panda",
        "gray_egg" to "slot",
        "teal_egg" to "hydra",
        "pink_egg" to "unicorn",
        "cyan_egg" to "kapybara",
        "white_egg" to "shark",
        "golden_egg" to "lion",
    )

    /**
     * Swedish species names, for the places that name the animal in prose rather than
     * drawing it — the parent dashboard's child cards, mainly.
     *
     * "slot" is the codebase's spelling of sloth, kept because it is the egg mapping
     * the backend and the art already use.
     */
    private val SPECIES_NAMES_SV = mapOf(
        "dragon" to "Drake",
        "cat" to "Katt",
        "dog" to "Hund",
        "bird" to "Fågel",
        "rabbit" to "Kanin",
        "bear" to "Björn",
        "snake" to "Orm",
        "panda" to "Panda",
        "slot" to "Sengångare",
        "hydra" to "Hydra",
        "unicorn" to "Enhörning",
        "kapybara" to "Kapybara",
        "shark" to "Haj",
        "lion" to "Lejon",
    )

    /** Null for an unknown species, so the caller can decide what to say instead. */
    fun speciesName(petType: String?): String? =
        petTypeForEgg(petType)?.let { SPECIES_NAMES_SV[it] }

    private const val MIN_STAGE = 1
    private const val MAX_STAGE = 5

    /** Resource ids never change at runtime, and getIdentifier is too slow to recompose against. */
    private val idCache = ConcurrentHashMap<String, Int>()

    private fun Context.drawableId(name: String): Int =
        idCache.getOrPut(name) { resources.getIdentifier(name, "drawable", packageName) }

    /** "dragon" for "blue_egg". Also accepts a petType directly, since the API returns both. */
    fun petTypeForEgg(eggType: String?): String? {
        val key = eggType?.lowercase() ?: return null
        EGG_TO_PET[key]?.let { return it }
        return key.takeIf { it in EGG_TO_PET.values }
    }

    fun eggDrawable(context: Context, eggType: String?, stage: Int = MIN_STAGE): Int? {
        val petType = petTypeForEgg(eggType) ?: return null
        val safeStage = stage.coerceIn(MIN_STAGE, MAX_STAGE)
        return context.drawableId("${petType}_egg_stage$safeStage").takeIf { it != 0 }
    }

    /**
     * Standalone transparent pet art.
     *
     * Falls back to the nearest lower stage when one is missing, so a gap in the art
     * shows the previous stage instead of an empty screen. unicorn_stage5 relies on
     * this today — it does not exist in the source art.
     */
    fun petDrawable(context: Context, petType: String?, growthStage: Int): Int? {
        val species = petType?.lowercase() ?: return null
        for (stage in growthStage.coerceIn(MIN_STAGE, MAX_STAGE) downTo MIN_STAGE) {
            val id = context.drawableId("${species}_stage$stage")
            if (id != 0) return id
        }
        return null
    }

    fun petDrawableFromEgg(context: Context, eggType: String?, growthStage: Int): Int? =
        petDrawable(context, petTypeForEgg(eggType), growthStage)

    /** Mar–May spring, Jun–Aug summer, Sep–Nov autumn, otherwise winter — as on web. */
    fun currentSeason(): String =
        when (Calendar.getInstance().get(Calendar.MONTH) + 1) {
            in 3..5 -> "spring"
            in 6..8 -> "summer"
            in 9..11 -> "autumn"
            else -> "winter"
        }

    fun seasonalBackgroundDrawable(context: Context, season: String = currentSeason()): Int? =
        context.drawableId("season_$season").takeIf { it != 0 }
}
