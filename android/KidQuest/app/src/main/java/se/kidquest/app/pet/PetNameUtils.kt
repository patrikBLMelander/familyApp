package se.kidquest.app.pet

import se.kidquest.app.R
import se.kidquest.app.i18n.tr

/** Species names in the UI language. The function names are historical. */
object PetNameUtils {
    private val names = mapOf(
        "dragon" to R.string.pet_name_dragon,
        "cat" to R.string.pet_name_cat,
        "dog" to R.string.pet_name_dog,
        "bird" to R.string.pet_name_bird,
        "rabbit" to R.string.pet_name_rabbit,
        "bear" to R.string.pet_name_bear,
        "snake" to R.string.pet_name_snake,
        "panda" to R.string.pet_name_panda,
        "slot" to R.string.pet_name_slot,
        "hydra" to R.string.pet_name_hydra,
        "unicorn" to R.string.pet_name_unicorn,
        "kapybara" to R.string.pet_name_kapybara,
        "shark" to R.string.pet_name_shark,
        "lion" to R.string.pet_name_lion,
        "koala" to R.string.pet_name_koala,
        "meerkat" to R.string.pet_name_meerkat,
        "penguin" to R.string.pet_name_penguin,
        "spider" to R.string.pet_name_spider,
        "kangaroo" to R.string.pet_name_kangaroo,
        "scorpion" to R.string.pet_name_scorpion,
        "octopus" to R.string.pet_name_octopus,
        "snowleopard" to R.string.pet_name_snowleopard,
        "tiger" to R.string.pet_name_tiger,
        "polarbear" to R.string.pet_name_polarbear,
        "giraffe" to R.string.pet_name_giraffe,
        "elephant" to R.string.pet_name_elephant,
        "crocodile" to R.string.pet_name_crocodile,
        "panther" to R.string.pet_name_panther,
        "wolf" to R.string.pet_name_wolf,
    )

    private val lowercaseNames = mapOf(
        "dragon" to R.string.pet_name_lower_dragon,
        "cat" to R.string.pet_name_lower_cat,
        "dog" to R.string.pet_name_lower_dog,
        "bird" to R.string.pet_name_lower_bird,
        "rabbit" to R.string.pet_name_lower_rabbit,
        "bear" to R.string.pet_name_lower_bear,
        "snake" to R.string.pet_name_lower_snake,
        "panda" to R.string.pet_name_lower_panda,
        "slot" to R.string.pet_name_lower_slot,
        "hydra" to R.string.pet_name_lower_hydra,
        "unicorn" to R.string.pet_name_lower_unicorn,
        "kapybara" to R.string.pet_name_lower_kapybara,
        "shark" to R.string.pet_name_lower_shark,
        "lion" to R.string.pet_name_lower_lion,
        "koala" to R.string.pet_name_lower_koala,
        "meerkat" to R.string.pet_name_lower_meerkat,
        "penguin" to R.string.pet_name_lower_penguin,
        "spider" to R.string.pet_name_lower_spider,
        "kangaroo" to R.string.pet_name_lower_kangaroo,
        "scorpion" to R.string.pet_name_lower_scorpion,
        "octopus" to R.string.pet_name_lower_octopus,
        "snowleopard" to R.string.pet_name_lower_snowleopard,
        "tiger" to R.string.pet_name_lower_tiger,
        "polarbear" to R.string.pet_name_lower_polarbear,
        "giraffe" to R.string.pet_name_lower_giraffe,
        "elephant" to R.string.pet_name_lower_elephant,
        "crocodile" to R.string.pet_name_lower_crocodile,
        "panther" to R.string.pet_name_lower_panther,
        "wolf" to R.string.pet_name_lower_wolf,
    )

    /** Capitalised species name, e.g. "Katt" / "Cat". */
    fun getPetNameSwedish(petType: String?): String =
        names[petType?.lowercase()]?.let { tr(it) } ?: (petType ?: tr(R.string.pet_name_unknown))

    /** Species name as used mid-sentence (lowercase except in German). */
    fun getPetNameSwedishLowercase(petType: String?): String =
        lowercaseNames[petType?.lowercase()]?.let { tr(it) } ?: (petType ?: tr(R.string.pet_name_lower_unknown))

    /** Null for an unknown species. */
    fun nameOrNull(petType: String?): String? =
        names[petType?.lowercase()]?.let { tr(it) }
}
