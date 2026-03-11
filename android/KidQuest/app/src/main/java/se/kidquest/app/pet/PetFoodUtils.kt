package se.kidquest.app.pet

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
    )

    private val foodNameMap = mapOf(
        "dragon" to "eldbär",
        "cat" to "fisk",
        "dog" to "ben",
        "bird" to "frön",
        "rabbit" to "morötter",
        "bear" to "honung",
        "snake" to "ägg",
        "panda" to "bambu",
        "slot" to "löv",
        "hydra" to "vattendroppar",
        "unicorn" to "stjärnfrukter",
        "kapybara" to "gräs",
    )

    fun getPetFoodEmoji(petType: String?): String =
        foodEmojiMap[petType?.lowercase()] ?: "🍎"

    fun getPetFoodName(petType: String?): String =
        foodNameMap[petType?.lowercase()] ?: "mat"
}
