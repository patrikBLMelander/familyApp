package se.kidquest.app.pet

import se.kidquest.app.R
import se.kidquest.app.i18n.tr

/**
 * Vad äggen viskar innan de kläcks.
 *
 * Låg fil-privat i ChildDashboardScreen, vilket gjorde att ingenting kunde kontrollera
 * att listan var komplett -- och den var det inte: lejonet och hajen lades till på
 * servern och ledtrådarna följde aldrig med. Här ligger de bredvid artnamnen, och iOS
 * har samma uppdelning i PetHelpers.swift.
 *
 * Här fanns också label(), ett namn per ägg. Namnen kom från serverns identifierare,
 * som är färger, medan konsten ritas per art -- så "Rött ägg" var beige med ett blått
 * tassavtryck. Rutorna visar nu bara ägget, och ledtråden är den enda texten kvar.
 */
object EggNames {

    fun hint(eggType: String): String =
        tr(
            when (eggType.lowercase()) {
                "blue_egg" -> R.string.egg_hint_blue_egg
                "green_egg" -> R.string.egg_hint_green_egg
                "red_egg" -> R.string.egg_hint_red_egg
                "yellow_egg" -> R.string.egg_hint_yellow_egg
                "purple_egg" -> R.string.egg_hint_purple_egg
                "orange_egg" -> R.string.egg_hint_orange_egg
                "brown_egg" -> R.string.egg_hint_brown_egg
                "black_egg" -> R.string.egg_hint_black_egg
                "gray_egg" -> R.string.egg_hint_gray_egg
                "teal_egg" -> R.string.egg_hint_teal_egg
                "pink_egg" -> R.string.egg_hint_pink_egg
                "cyan_egg" -> R.string.egg_hint_cyan_egg
                "golden_egg" -> R.string.egg_hint_golden_egg
                "white_egg" -> R.string.egg_hint_white_egg
                "silver_egg" -> R.string.egg_hint_silver_egg
                "sand_egg" -> R.string.egg_hint_sand_egg
                "ice_egg" -> R.string.egg_hint_ice_egg
                "amber_egg" -> R.string.egg_hint_amber_egg
                "clay_egg" -> R.string.egg_hint_clay_egg
                "ember_egg" -> R.string.egg_hint_ember_egg
                "indigo_egg" -> R.string.egg_hint_indigo_egg
                "frost_egg" -> R.string.egg_hint_frost_egg
                "tiger_egg" -> R.string.egg_hint_tiger_egg
                "snow_egg" -> R.string.egg_hint_snow_egg
                "savanna_egg" -> R.string.egg_hint_savanna_egg
                "ivory_egg" -> R.string.egg_hint_ivory_egg
                "swamp_egg" -> R.string.egg_hint_swamp_egg
                "onyx_egg" -> R.string.egg_hint_onyx_egg
                "moon_egg" -> R.string.egg_hint_moon_egg
                else -> R.string.egg_hint_default
            }
        )
}
