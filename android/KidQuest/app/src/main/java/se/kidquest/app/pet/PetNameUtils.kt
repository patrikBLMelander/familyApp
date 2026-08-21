package se.kidquest.app.pet

object PetNameUtils {
    private val petNameMap = mapOf(
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

    private val petNameLowercaseMap = mapOf(
        "dragon" to "drake",
        "cat" to "katt",
        "dog" to "hund",
        "bird" to "fågel",
        "rabbit" to "kanin",
        "bear" to "björn",
        "snake" to "orm",
        "panda" to "panda",
        "slot" to "sengångare",
        "hydra" to "hydra",
        "unicorn" to "enhörning",
        "kapybara" to "kapybara",
        "shark" to "haj",
        "lion" to "lejon",
    )

    fun getPetNameSwedish(petType: String?): String =
        petNameMap[petType?.lowercase()] ?: (petType ?: "Djur")

    fun getPetNameSwedishLowercase(petType: String?): String =
        petNameLowercaseMap[petType?.lowercase()] ?: (petType ?: "djur")
}

