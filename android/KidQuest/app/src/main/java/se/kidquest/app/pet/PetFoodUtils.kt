package se.kidquest.app.pet

import se.kidquest.app.R
import se.kidquest.app.i18n.tr

object PetFoodUtils {
    private val foodEmojiMap = mapOf(
        "dragon" to "🔥",
        "cat" to "🐟",
        "dog" to "🦴",
        "bird" to "🌾",
        "rabbit" to "🥕",
        "bear" to "🍯",
        "snake" to "🥚",
        "panda" to "🎋",
        "slot" to "🍃",
        "hydra" to "💧",
        "unicorn" to "✨",
        "kapybara" to "🌿",
        "shark" to "🐠",
        "lion" to "🥩",
        "scorpion" to "🦗",
        "octopus" to "🦐",
        "snowleopard" to "🥩",
        "tiger" to "🍖",
        "polarbear" to "🐟",
        "giraffe" to "🌿",
        "elephant" to "🥜",
        "crocodile" to "🍖",
        "panther" to "🥩",
        "wolf" to "🍖",
    )

    private val foodNameMap = mapOf(
        "dragon" to R.string.pet_food_dragon,
        "cat" to R.string.pet_food_cat,
        "dog" to R.string.pet_food_dog,
        "bird" to R.string.pet_food_bird,
        "rabbit" to R.string.pet_food_rabbit,
        "bear" to R.string.pet_food_bear,
        "snake" to R.string.pet_food_snake,
        "panda" to R.string.pet_food_panda,
        "slot" to R.string.pet_food_slot,
        "hydra" to R.string.pet_food_hydra,
        "unicorn" to R.string.pet_food_unicorn,
        "kapybara" to R.string.pet_food_kapybara,
        "shark" to R.string.pet_food_shark,
        "lion" to R.string.pet_food_lion,
        "scorpion" to R.string.pet_food_scorpion,
        "octopus" to R.string.pet_food_octopus,
        "snowleopard" to R.string.pet_food_snowleopard,
        "tiger" to R.string.pet_food_tiger,
        "polarbear" to R.string.pet_food_polarbear,
        "giraffe" to R.string.pet_food_giraffe,
        "elephant" to R.string.pet_food_elephant,
        "crocodile" to R.string.pet_food_crocodile,
        "panther" to R.string.pet_food_panther,
        "wolf" to R.string.pet_food_wolf,
    )

    fun getPetFoodEmoji(petType: String?): String =
        foodEmojiMap[petType?.lowercase()] ?: "🍎"

    fun getPetFoodName(petType: String?): String =
        foodNameMap[petType?.lowercase()]?.let { tr(it) } ?: tr(R.string.pet_food_default)
}
