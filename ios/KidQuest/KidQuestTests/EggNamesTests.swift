import Testing
@testable import KidQuest

/// Äggnamnen är en kopia av en lista som bor på servern, och en kopia glider.
///
/// Den gjorde det: lejonet och hajen lades till i `EGG_TO_PET_MAP` och namnen följde
/// aldrig med, så äggväljaren visade `golden_egg` och `white_egg` — med understreck,
/// mitt i det första ett barn ser av appen. Ingenting kunde upptäcka det, eftersom
/// funktionen låg privat inne i arket.
@Suite("Äggnamn")
struct EggNamesTests {

    /// Serverns fjorton ägg, ur `PetService.EGG_TO_PET_MAP`. Listan är med flit skriven
    /// för hand: en ny rad här är det som ska tvinga fram ett nytt namn nedan.
    static let allEggs = [
        "black_egg", "blue_egg", "brown_egg", "cyan_egg", "golden_egg", "gray_egg",
        "green_egg", "orange_egg", "pink_egg", "purple_egg", "red_egg", "teal_egg",
        "white_egg", "yellow_egg",
    ]

    @Test("varje ägg har ett svenskt namn")
    func everyEggIsNamed() {
        for egg in Self.allEggs {
            let namn = EggNames.label(for: egg)
            #expect(namn != egg, "\(egg) saknar namn och visas som sin identifierare")
            #expect(namn != "Ägg", "\(egg) faller igenom till reservnamnet")
            #expect(namn.hasSuffix("ägg"), "\(egg) heter \(namn), vilket inte slutar på ägg")
        }
    }

    /// Det som faktiskt gick fel var att en identifierare nådde skärmen. Understreck och
    /// bindestreck är hur en identifierare ser ut; ett namn ett barn ska läsa har inga.
    @Test("inget namn innehåller understreck eller bindestreck")
    func namesReadAsSwedish() {
        for egg in Self.allEggs {
            let namn = EggNames.label(for: egg)
            #expect(!namn.contains("_"), "\(egg) -> \(namn)")
            #expect(!namn.contains("-"), "\(egg) -> \(namn)")
            #expect(namn.first?.isUppercase == true, "\(egg) -> \(namn) börjar inte med versal")
        }
    }

    @Test("varje ägg har ett eget tips")
    func everyEggHints() {
        var seen = Set<String>()
        for egg in Self.allEggs {
            let tips = EggNames.hint(for: egg)
            #expect(tips != "Jag längtar efter att få träffa dig.", "\(egg) faller igenom till reservtipset")
            #expect(tips.hasPrefix("Jag "), "\(egg) -> \(tips)")
            #expect(tips.hasSuffix("."), "\(egg) -> \(tips)")
            #expect(seen.insert(tips).inserted, "\(egg) delar tips med ett annat ägg")
        }
    }

    /// Ett ägg servern lägger till i morgon ska mötas av något läsbart, inte av sitt eget
    /// id. Reserven är det som gör att nästa glidning blir ful i stället för trasig.
    @Test("okänt ägg avslöjar aldrig sin identifierare")
    func unknownEggFallsBackCleanly() {
        for okänt in ["rainbow_egg", "SPARKLY_EGG", "", "nonsense"] {
            let namn = EggNames.label(for: okänt)
            #expect(namn == "Ägg")
            #expect(!namn.contains("_"))
            #expect(EggNames.hint(for: okänt).hasPrefix("Jag "))
        }
    }
}
