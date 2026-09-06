package se.kidquest.app.pet

/**
 * Vad äggen heter för ett barn, och vad de viskar innan de kläcks.
 *
 * Låg fil-privat i ChildDashboardScreen, vilket gjorde att ingenting kunde kontrollera
 * att listan var komplett -- och den var det inte: lejonet och hajen lades till på
 * servern och namnen följde aldrig med, så väljaren visade "golden_egg" och "white_egg"
 * med understreck. Här ligger de bredvid artnamnen, och iOS har samma uppdelning i
 * PetHelpers.swift.
 */
object EggNames {

    fun label(eggType: String): String =
        when (eggType.lowercase()) {
            "blue_egg" -> "Blått ägg"
            "green_egg" -> "Grönt ägg"
            "red_egg" -> "Rött ägg"
            "yellow_egg" -> "Gult ägg"
            "purple_egg" -> "Lila ägg"
            "orange_egg" -> "Orange ägg"
            "brown_egg" -> "Brunt ägg"
            "black_egg" -> "Mörkt ägg"
            "gray_egg" -> "Grått ägg"
            "teal_egg" -> "Turkost ägg"
            "pink_egg" -> "Rosa ägg"
            "cyan_egg" -> "Blågrönt ägg"
            "golden_egg" -> "Gyllene ägg"
            "white_egg" -> "Vitt ägg"
            // Aldrig eggType. Föll ett ägg igenom stod det "golden_egg" med understreck
            // mitt i äggväljaren, vilket är precis vad som hände när lejonet och hajen
            // lades till på servern och namnen inte följde med. En reserv som lyder är
            // bättre än en som avslöjar en identifierare.
            else -> "Ägg"
    }

    fun hint(eggType: String): String =
        when (eggType.lowercase()) {
            "blue_egg" -> "Jag älskar att flyga högt bland molnen."
            "green_egg" -> "Jag spinner nöjt när jag får ligga i solen."
            "red_egg" -> "Jag hämtar gärna bollen om du kastar den."
            "yellow_egg" -> "Jag kvittrar gärna när dagen börjar."
            "purple_egg" -> "Jag hoppar fram och gnager gärna på morötter."
            "orange_egg" -> "Jag tar gärna en lång vintersömn med magen full."
            "brown_egg" -> "Jag gillar att slingra mig på varma stenar."
            "black_egg" -> "Jag tycker om att smyga runt i skuggan."
            "gray_egg" -> "Jag rör mig långsamt men kramas gärna länge."
            "teal_egg" -> "Jag trivs där det finns mycket vatten och mystik."
            "pink_egg" -> "Jag gillar glitter, regnbågar och magi."
            "cyan_egg" -> "Jag älskar att plaska runt med kompisar."
            "golden_egg" -> "Jag ryter så att alla hör att jag vaknat."
            "white_egg" -> "Jag simmar snabbast av alla där det är djupt."
            else -> "Jag längtar efter att få träffa dig."
    }
}
