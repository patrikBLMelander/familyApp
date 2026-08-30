package se.kidquest.app.pet

import android.content.Context
import java.time.LocalDate
import java.time.MonthDay

/**
 * The backdrop a pet is drawn against, and how bright it is.
 *
 * This used to be a season name resolved from the month, which was enough while there
 * were exactly four backdrops and all four were mid-toned. It stops being enough the
 * moment a Christmas scene arrives: the pet screen draws its labels in white, which
 * reads on a summer beach and disappears on snow. Every backdrop therefore declares
 * whether it is dark, and whatever is drawn on top asks rather than assumes.
 *
 * That one field is the difference between a new theme being a picture and a line, and
 * a new theme being a bug nobody notices until a family reports it.
 *
 * @param drawableName the file in res/drawable, without extension.
 * @param dark true when the picture is dark enough for white text. A snowy scene is
 *   not; a cave is.
 */
data class PetScene(
    val drawableName: String,
    val dark: Boolean,
)

object PetScenes {

    /**
     * Named windows that outrank the season. Dates are inclusive.
     *
     * Swedish practice rather than the calendar's: Christmas starts at Advent and runs
     * to Epiphany, because that is when the decorations are actually up. Easter moves
     * with the moon and is not in this table -- see [easterWindowContains].
     *
     * Adding one is a row here plus a drawing in res/drawable. Nothing else.
     */
    private val holidays: List<Triple<MonthDay, MonthDay, PetScene>> = listOf(
        Triple(
            MonthDay.of(12, 1), MonthDay.of(1, 6),
            PetScene("scene_jul", dark = false),
        ),
    )

    /**
     * @param adventure set while a pet is away somewhere -- a cave, a forest. Outranks
     *   everything, because where the animal IS beats what month it is. Nothing sets
     *   this yet; the slot exists so that feature does not have to reopen this file.
     * @param today injectable so the windows can be tested without waiting for December.
     */
    fun current(
        context: Context,
        adventure: PetScene? = null,
        today: LocalDate = LocalDate.now(),
    ): PetScene {
        adventure?.let { if (exists(context, it)) return it }

        holidayFor(today)?.let { if (exists(context, it)) return it }

        // The season is the floor, and its four drawings are the ones that certainly
        // exist. Anything above may still be unbuilt art, which is why each is checked.
        return PetScene(
            drawableName = "season_${PetImages.currentSeason()}",
            dark = false,
        )
    }

    private fun holidayFor(today: LocalDate): PetScene? {
        val md = MonthDay.of(today.monthValue, today.dayOfMonth)
        for ((from, to, scene) in holidays) {
            val wraps = to < from
            val inside = if (wraps) md >= from || md <= to else md in from..to
            if (inside) return scene
        }
        return null
    }

    private fun exists(context: Context, scene: PetScene): Boolean =
        context.resources.getIdentifier(
            scene.drawableName, "drawable", context.packageName,
        ) != 0
}
