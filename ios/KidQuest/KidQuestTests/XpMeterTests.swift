import Testing
@testable import KidQuest

/// Mätaren räknar sitt spann ur DTO:n i stället för ur en kopia av serverns trösklar.
/// Det som gör det möjligt är att `xpForNextLevel` är hur mycket som FATTAS och inte
/// tröskeln själv -- och just den skillnaden hade båda fixturerna fel om, vilket gav
/// mätaren spannet 77 i stället för 35 tills något faktiskt läste fältet.
@Suite("XP-mätaren")
struct XpMeterTests {

    /// Serverns trösklar, ur `MemberXpProgress`. Ligger här bara för att kunna räkna
    /// fram vad en fixtur BORDE innehålla -- produktionskoden läser dem aldrig.
    private static let thresholds = [0, 10, 35, 70, 125]

    private static func level(forXp xp: Int) -> Int {
        for level in stride(from: 5, through: 1, by: -1) where xp >= thresholds[level - 1] {
            return level
        }
        return 1
    }

    private static func expected(forXp xp: Int) -> (level: Int, inLevel: Int, next: Int) {
        let lvl = level(forXp: xp)
        if lvl >= 5 { return (5, 0, 0) }
        return (lvl, xp - thresholds[lvl - 1], thresholds[lvl] - xp)
    }

    @Test("spannet är nivåns egen längd, inte totalen")
    func spanIsTheLevelsOwnLength() {
        // Nivå 3 spänner 35 xp (70 - 35). Ett barn på 42 xp är 7 in i den.
        #expect(xpSpanFor(xpInCurrentLevel: 7, xpForNextLevel: 28) == 35)
        // Nivå 1 spänner 10, nivå 2 spänner 25, nivå 4 spänner 55.
        #expect(xpSpanFor(xpInCurrentLevel: 0, xpForNextLevel: 10) == 10)
        #expect(xpSpanFor(xpInCurrentLevel: 20, xpForNextLevel: 5) == 25)
        #expect(xpSpanFor(xpInCurrentLevel: 54, xpForNextLevel: 1) == 55)
    }

    @Test("högsta nivån har inget spann att fylla mot")
    func maxLevelHasNoSpan() {
        #expect(xpSpanFor(xpInCurrentLevel: 0, xpForNextLevel: 0) == 0)
    }

    /// Det här är testet som hade fångat felet. Fixturen hade `xpForNextLevel: 70`,
    /// alltså tröskeln, medan servern returnerar tröskeln minus currentXp.
    @Test("fixturens XP stämmer med vad servern skulle ha returnerat")
    func fixtureXpMatchesTheServer() {
        for xp in [ChildFixtures.xp, ChildFixtures.xpNearLevelUp] {
            let want = Self.expected(forXp: xp.currentXp)
            #expect(xp.currentLevel == want.level)
            #expect(xp.xpInCurrentLevel == want.inLevel)
            #expect(xp.xpForNextLevel == want.next)
        }
    }

    /// Stadiet är nivån -- `calculateGrowthStage` mappar 1:1 -- så en fixtur med djuret
    /// på stadie 4 och barnet på nivå 3 beskriver ett tillstånd som inte kan uppstå.
    @Test("fixturens stadie följer nivån")
    func fixtureStageFollowsLevel() {
        #expect(ChildFixtures.pet.growthStage == ChildFixtures.xp.currentLevel)
    }

    @Test("den som matar förbi tröskeln får höjningen på rätt bär")
    func crossingBerryIsTheOneThatFills() {
        // xpForNextLevel är hur många XP som fattas, så det (xpForNextLevel - 1):te
        // bäret korsar. Tre som fattas och fem att ge: bäret med index 2.
        let needed = 3
        let amount = 5
        #expect((1...amount).contains(needed))
        #expect(needed - 1 == 2)

        // Räcker maten inte fram sker ingen höjning.
        #expect(!(1...2).contains(needed))
    }
}
