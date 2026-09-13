import Testing
@testable import KidQuest

/// Äggens ledtrådar och bilder är kopior av en lista som bor på servern, och en kopia
/// glider.
///
/// Den gjorde det två gånger. Först lades lejonet och hajen till i `EGG_TO_PET_MAP` och
/// namnen följde aldrig med, så äggväljaren visade `golden_egg` och `white_egg` — med
/// understreck, mitt i det första ett barn ser av appen. Sedan visade det sig att namnen
/// aldrig hade stämt med konsten: bilderna ritas per art, namnen kom från serverns
/// färgidentifierare, och nio av fjorton sa emot sin egen bild. Namnen är därför borta.
///
/// Kvar är två kopior som fortfarande kan glida, och bilden är nu den viktigare av dem:
/// utan namn är den allt som skiljer två ägg åt.
@Suite("Äggnamn")
struct EggNamesTests {

    /// Serverns fjorton ägg, ur `PetService.EGG_TO_PET_MAP`. Listan är med flit skriven
    /// för hand: en ny rad här är det som ska tvinga fram en ny ledtråd nedan.
    static let allEggs = [
        "amber_egg", "black_egg", "blue_egg", "brown_egg", "cyan_egg", "golden_egg", "gray_egg",
        "clay_egg",
        "green_egg", "ice_egg", "orange_egg", "pink_egg", "purple_egg", "red_egg", "sand_egg",
        "silver_egg", "teal_egg", "white_egg", "yellow_egg",
    ]

    /// Det här testet betyder mer sedan namnen togs bort. Ett ägg utan bild ritas som
    /// emojin 🥚, och två sådana i samma rutnät är omöjliga att skilja åt — förut räddade
    /// namnet under dem situationen, nu finns inget kvar att läsa.
    @Test("varje ägg har en egen bild")
    func everyEggHasDistinctArt() {
        var seen = Set<String>()
        for egg in Self.allEggs {
            let bild = PetImagesIOS.eggImageName(for: egg)
            #expect(bild != nil, "\(egg) saknar bild och ritas som en tom emoji")
            if let bild {
                #expect(seen.insert(bild).inserted, "\(egg) delar bild med ett annat ägg")
            }
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
            let tips = EggNames.hint(for: okänt)
            #expect(tips == "Jag längtar efter att få träffa dig.")
            #expect(!tips.contains("_"))
            #expect(PetImagesIOS.eggImageName(for: okänt) == nil)
        }
    }
}
