package se.kidquest.app.pet

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
            "silver_egg" -> "Jag sover gärna högt uppe bland eukalyptuslöven."
            "sand_egg" -> "Jag står på bakbenen och spanar efter faror."
            "ice_egg" -> "Jag vaggar fram på isen och dyker gärna efter fisk."
            "amber_egg" -> "Jag spinner nät och fångar flugor på morgonen."
            "clay_egg" -> "Jag hoppar långt och bär min unge i fickan."
            "ember_egg" -> "Jag har en stark svans och gömmer mig gärna bland stenar."
            "indigo_egg" -> "Jag har åtta armar och gömmer mig gärna bland koraller."
            "frost_egg" -> "Jag smyger tyst över snöiga berg och gömmer mig i dimman."
            "tiger_egg" -> "Jag har ränder och smyger tyst genom den höga gräsdjungeln."
            else -> "Jag längtar efter att få träffa dig."
    }
}
