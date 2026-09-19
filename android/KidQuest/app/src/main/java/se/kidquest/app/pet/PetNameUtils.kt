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
        "koala" to "Koala",
        "meerkat" to "Surikat",
        "penguin" to "Pingvin",
        "spider" to "Spindel",
        "kangaroo" to "Känguru",
        "scorpion" to "Skorpion",
        "octopus" to "Bläckfisk",
        "snowleopard" to "Snöleopard",
        "tiger" to "Tiger",
        "polarbear" to "Isbjörn",
        "giraffe" to "Giraff",
        "elephant" to "Elefant",
        "crocodile" to "Krokodil",
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
        "koala" to "koala",
        "meerkat" to "surikat",
        "penguin" to "pingvin",
        "spider" to "spindel",
        "kangaroo" to "känguru",
        "scorpion" to "skorpion",
        "octopus" to "bläckfisk",
        "snowleopard" to "snöleopard",
        "tiger" to "tiger",
        "polarbear" to "isbjörn",
        "giraffe" to "giraff",
        "elephant" to "elefant",
        "crocodile" to "krokodil",
    )

    fun getPetNameSwedish(petType: String?): String =
        petNameMap[petType?.lowercase()] ?: (petType ?: "Djur")

    fun getPetNameSwedishLowercase(petType: String?): String =
        petNameLowercaseMap[petType?.lowercase()] ?: (petType ?: "djur")
}

